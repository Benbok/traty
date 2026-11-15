package ru.my.traty.data.weekly

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyProgressTest {
    private val mon = LocalDate.of(2026, 11, 16)
    private fun task(type: WeeklyTaskType, target: Long, usualKopecks: Long = 0, checked: Boolean = false, week: LocalDate = mon) =
        WeeklyTask(weekStart = week.toEpochDay(), type = type.name, categoryId = 1, title = "t", target = target, usualKopecks = usualKopecks, checked = checked)

    private fun spend(day: Int, amount: Long = 50_000, cat: Long = 1) = TaskSpend(cat, amount, mon.plusDays(day.toLong()))

    @Test
    fun limitInProgressDoneAndFailed() {
        val t = task(WeeklyTaskType.CATEGORY_LIMIT, 200_000, usualKopecks = 260_000)
        val spends = listOf(spend(0), spend(2), spend(3, cat = 2))
        assertEquals(TaskState.IN_PROGRESS, WeeklyProgress.of(t, spends, mon.plusDays(3)).state)
        val done = WeeklyProgress.of(t, spends, mon.plusDays(7))
        assertEquals(TaskState.DONE, done.state)
        assertEquals(160_000L, done.savedKopecks)
        val failed = WeeklyProgress.of(t, spends + spend(4, 150_000), mon.plusDays(5))
        assertEquals(TaskState.FAILED, failed.state)
        assertEquals(0L, failed.savedKopecks)
    }

    @Test
    fun noSpendDaysDoneEarlyAndFailedWhenImpossible() {
        val t = task(WeeklyTaskType.NO_SPEND_DAYS, 3)
        // Пн — трата, Вт, Ср, Чт — без трат: к четвергу задание выполнено
        assertEquals(TaskState.DONE, WeeklyProgress.of(t, listOf(spend(0)), mon.plusDays(3)).state)
        assertEquals(TaskState.IN_PROGRESS, WeeklyProgress.of(t, listOf(spend(0)), mon.plusDays(1)).state)
        // Траты в 5 разных дней — больше 2 свободных не будет
        val busy = (0..4).map { spend(it) }
        assertEquals(TaskState.FAILED, WeeklyProgress.of(t, busy, mon.plusDays(4)).state)
    }

    @Test
    fun countLimit() {
        val t = task(WeeklyTaskType.COUNT_LIMIT, 2)
        assertEquals(TaskState.IN_PROGRESS, WeeklyProgress.of(t, listOf(spend(0), spend(1)), mon.plusDays(2)).state)
        assertEquals(TaskState.FAILED, WeeklyProgress.of(t, listOf(spend(0), spend(1), spend(2)), mon.plusDays(2)).state)
    }

    @Test
    fun reviewIsManual() {
        assertEquals(TaskState.DONE, WeeklyProgress.of(task(WeeklyTaskType.REVIEW_SUBSCRIPTIONS, 3, checked = true), emptyList(), mon).state)
        assertEquals(TaskState.FAILED, WeeklyProgress.of(task(WeeklyTaskType.REVIEW_SUBSCRIPTIONS, 3), emptyList(), mon.plusDays(7)).state)
    }

    @Test
    fun resultsAndStreak() {
        val today = mon.plusDays(2)
        val prev = mon.minusWeeks(1)
        val prev2 = mon.minusWeeks(2)
        val prev3 = mon.minusWeeks(3)
        val tasks = listOf(
            task(WeeklyTaskType.COUNT_LIMIT, 5, week = mon),
            task(WeeklyTaskType.COUNT_LIMIT, 5, usualKopecks = 100_000, week = prev),
            task(WeeklyTaskType.COUNT_LIMIT, 5, week = prev2),
            task(WeeklyTaskType.COUNT_LIMIT, 0, week = prev3),
            task(WeeklyTaskType.COUNT_LIMIT, 0, week = prev2).copy(active = false),
        )
        val spends = listOf(TaskSpend(1, 50_000, prev3), TaskSpend(1, 50_000, prev2))
        val results = WeeklyProgress.results(tasks, spends, today)
        assertEquals(listOf(mon, prev, prev2, prev3), results.map { it.weekStart })
        assertEquals(false, results[0].finished)
        assertEquals(100_000L, results[1].savedKopecks)
        assertEquals(false, results[3].success)
        // Две прошлые недели выполнены, третья — нет
        assertEquals(2, WeeklyProgress.streak(results, today))
    }
}

class WeeklyTaskTextsTest {
    private fun ru(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun customTaskTitlesAndMonday() {
        val wed = LocalDate.of(2026, 11, 18)
        val t = WeeklyTaskTexts.customTask(wed, WeeklyTaskType.COUNT_LIMIT, 2, "Такси", 2)
        assertEquals("«Такси» — не больше 2 раз", t.title)
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), t.weekStart)
        assertEquals(true, t.custom)
        assertEquals("«Кафе» — не больше 2 000 ₽", ru(WeeklyTaskTexts.customTask(wed, WeeklyTaskType.CATEGORY_LIMIT, 1, "Кафе", 200_000).title))
        assertEquals("«Кафе» — 1 день без трат", WeeklyTaskTexts.customTask(wed, WeeklyTaskType.NO_SPEND_DAYS, 1, "Кафе", 1).title)
    }

    @Test
    fun resultTitleCountsDoneAndSaved() {
        val mon = LocalDate.of(2026, 11, 16)
        val a = WeeklyTask(weekStart = mon.toEpochDay(), type = WeeklyTaskType.COUNT_LIMIT.name, categoryId = 1, title = "A", target = 5, usualKopecks = 100_000)
        val b = a.copy(title = "B", target = 0)
        val spends = listOf(TaskSpend(1, 30_000, mon))
        val (title, body) = WeeklyTaskTexts.weekResult(listOf(a, b).map { WeeklyProgress.of(it, spends, mon.plusDays(7)) })
        assertEquals("Итог недели: 1 из 2, сэкономлено 700 ₽", ru(title))
        assertEquals("✅ A\n❌ B", body)
        assertEquals("недели", WeeklyTaskTexts.weeksWord(3))
    }
}
