package ru.my.traty.data.weekly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyTaskGeneratorTest {
    /** Понедельник. */
    private val week = LocalDate.of(2026, 11, 16)
    private val cafe = TaskCategory(1, "Кафе", impulsive = true)
    private val taxi = TaskCategory(2, "Такси", impulsive = false)
    private val food = TaskCategory(3, "Продукты", impulsive = false)
    private val cats = listOf(cafe, taxi, food)

    private fun ru(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    /** [weeks] недель до [week]: в каждой — траты по дням недели [days] (0 = понедельник). */
    private fun weekly(cat: Long, amount: Long, days: List<Int>, weeks: Int = 8) = (1..weeks).flatMap { w ->
        days.map { d -> TaskSpend(cat, amount, week.minusWeeks(w.toLong()).plusDays(d.toLong())) }
    }

    @Test
    fun shortHistoryGivesNoTasks() {
        val history = weekly(1, 100_000, listOf(0, 2), weeks = 2)
        assertTrue(WeeklyTaskGenerator.generate(week, history, cats).isEmpty())
    }

    @Test
    fun suggestsLimitDaysAndCountForDifferentCategories() {
        val history = weekly(1, 130_000, listOf(0, 3)) + // кафе 2 600 ₽ в неделю, 2 дня
            weekly(2, 40_000, listOf(0, 1, 2, 3)) + // такси 4 раза
            weekly(3, 150_000, listOf(0, 2, 4, 5)) // продукты
        val tasks = WeeklyTaskGenerator.generate(week, history, cats)
        assertEquals(3, tasks.size)
        assertEquals(3, tasks.mapNotNull { it.categoryId }.toSet().size)
        assertEquals(3, tasks.map { it.type }.toSet().size)
        val limit = tasks.first()
        assertEquals(WeeklyTaskType.CATEGORY_LIMIT.name, limit.type)
        assertEquals(1L, limit.categoryId) // импульсивная категория первой
        assertEquals(200_000L, limit.target) // 2 600 × 0,8 = 2 080 → 2 000
        assertEquals("«Кафе» — не больше 2 000 ₽ (обычно 2 600 ₽)", ru(limit.title))
        assertTrue(tasks.all { it.weekStart == week.toEpochDay() })
        val count = tasks.firstOrNull { it.type == WeeklyTaskType.COUNT_LIMIT.name }
        if (count != null) assertTrue(count.target < count.usual)
    }

    @Test
    fun replacementSkipsBusyCategoriesAndSeenTasks() {
        val history = weekly(1, 130_000, listOf(0, 3)) + weekly(2, 40_000, listOf(0, 1, 2, 3)) + weekly(3, 150_000, listOf(0, 2, 4, 5))
        val tasks = WeeklyTaskGenerator.generate(week, history, cats)
        val replaced = tasks.first().copy(active = false)
        val week2 = listOf(replaced) + tasks.drop(1)
        val r = WeeklyTaskGenerator.replacement(week, week2, history, cats)
        if (r != null) {
            assertTrue(r.categoryId !in tasks.drop(1).mapNotNull { it.categoryId })
            assertTrue((r.type to r.categoryId) != (replaced.type to replaced.categoryId))
        }
    }

    @Test
    fun subscriptionsReviewNeedsThree() {
        val history = weekly(1, 130_000, listOf(0, 3))
        val few = WeeklyTaskGenerator.generate(week, history, cats, SubscriptionsSummary(2, 100_000))
        assertTrue(few.none { it.type == WeeklyTaskType.REVIEW_SUBSCRIPTIONS.name })
        val many = WeeklyTaskGenerator.generate(week, history, cats, SubscriptionsSummary(4, 199_000))
        val review = many.single { it.type == WeeklyTaskType.REVIEW_SUBSCRIPTIONS.name }
        assertEquals("Пересмотреть подписки: 4 на 1 990 ₽ в месяц", ru(review.title))
        assertNull(review.categoryId)
    }

    @Test
    fun wordForms() {
        assertEquals("раза", WeeklyTaskGenerator.timesWord(1))
        assertEquals("раз", WeeklyTaskGenerator.timesWord(2))
        assertEquals("раз", WeeklyTaskGenerator.timesWord(11))
        assertEquals("дня", WeeklyTaskGenerator.daysWord(3))
        assertEquals("дней", WeeklyTaskGenerator.daysWord(5))
    }
}
