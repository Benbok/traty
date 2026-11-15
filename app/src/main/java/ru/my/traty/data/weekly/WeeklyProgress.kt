package ru.my.traty.data.weekly

import java.time.LocalDate

enum class TaskState { IN_PROGRESS, DONE, FAILED }

/** Как идёт задание: [fraction] 0..1 для полосы, [text] — подпись, [savedKopecks] — сколько сэкономлено против обычного. */
data class TaskProgress(
    val task: WeeklyTask,
    val state: TaskState,
    val fraction: Float,
    val text: String,
    val savedKopecks: Long,
)

/** Итог недели. [finished] — неделя прошла; [success] — все задания выполнены. */
data class WeekResult(val weekStart: LocalDate, val done: Int, val total: Int, val savedKopecks: Long, val finished: Boolean) {
    val success: Boolean get() = total > 0 && done == total
}

/** Прогресс заданий по тратам недели (docs/PLAN.md, этап 8). Чистый код, в базе ничего не хранится. */
object WeeklyProgress {
    /** [spends] — траты любых дат: берутся только дни недели задания. */
    fun of(task: WeeklyTask, spends: List<TaskSpend>, today: LocalDate): TaskProgress {
        val start = LocalDate.ofEpochDay(task.weekStart)
        val end = start.plusDays(6)
        val finished = today.isAfter(end)
        val mine = spends.filter { it.categoryId == task.categoryId && !it.date.isBefore(start) && !it.date.isAfter(end) }
        val spent = mine.sumOf { it.amountKopecks }
        val saved = (task.usualKopecks - spent).coerceAtLeast(0)
        val target = task.target.coerceAtLeast(1)
        return when (WeeklyTaskType.of(task.type)) {
            WeeklyTaskType.CATEGORY_LIMIT -> {
                val state = when {
                    spent > task.target -> TaskState.FAILED
                    finished -> TaskState.DONE
                    else -> TaskState.IN_PROGRESS
                }
                val text = if (state == TaskState.FAILED) "Потрачено ${rub(spent)}, больше на ${rub(spent - task.target)}"
                else "Потрачено ${rub(spent)} из ${rub(task.target)}"
                TaskProgress(task, state, spent.toFloat() / target, text, if (state == TaskState.FAILED) 0 else saved)
            }
            WeeklyTaskType.COUNT_LIMIT -> {
                val count = mine.size.toLong()
                val state = when {
                    count > task.target -> TaskState.FAILED
                    finished -> TaskState.DONE
                    else -> TaskState.IN_PROGRESS
                }
                TaskProgress(task, state, count.toFloat() / target, "Трат: $count из ${task.target}", if (state == TaskState.FAILED) 0 else saved)
            }
            WeeklyTaskType.NO_SPEND_DAYS -> {
                val last = if (finished) end else today
                val elapsed = if (today.isBefore(start)) 0L else java.time.temporal.ChronoUnit.DAYS.between(start, last) + 1
                val busyDays = mine.map { it.date }.filter { !it.isAfter(last) }.distinct().size.toLong()
                val allBusy = mine.map { it.date }.distinct().size.toLong()
                val free = (elapsed - busyDays).coerceAtLeast(0)
                val state = when {
                    7 - allBusy < task.target -> TaskState.FAILED
                    free >= task.target || finished -> TaskState.DONE
                    else -> TaskState.IN_PROGRESS
                }
                val text = "Дней без трат: $free из ${task.target}"
                TaskProgress(task, state, free.toFloat() / target, text, if (state == TaskState.FAILED) 0 else saved)
            }
            WeeklyTaskType.REVIEW_SUBSCRIPTIONS, null -> {
                val state = when {
                    task.checked -> TaskState.DONE
                    finished -> TaskState.FAILED
                    else -> TaskState.IN_PROGRESS
                }
                val text = if (task.checked) "Отмечено" else "Отметьте, когда пересмотрите подписки"
                TaskProgress(task, state, if (task.checked) 1f else 0f, text, 0)
            }
        }
    }

    /** Итоги по неделям, новые сверху. Учитываются только действующие задания. */
    fun results(tasks: List<WeeklyTask>, spends: List<TaskSpend>, today: LocalDate): List<WeekResult> =
        tasks.filter { it.active }.groupBy { it.weekStart }.toSortedMap(reverseOrder()).map { (week, list) ->
            val start = LocalDate.ofEpochDay(week)
            val progress = list.map { of(it, spends, today) }
            val done = progress.filter { it.state == TaskState.DONE }
            WeekResult(start, done.size, list.size, done.sumOf { it.savedKopecks }, today.isAfter(start.plusDays(6)))
        }

    /** Серия: сколько прошедших недель подряд, до текущей, все задания были выполнены. */
    fun streak(results: List<WeekResult>, today: LocalDate): Int {
        var expected = Weeks.monday(today).minusWeeks(1)
        var n = 0
        for (r in results.filter { it.finished }.sortedByDescending { it.weekStart }) {
            if (r.weekStart != expected || !r.success) break
            n++
            expected = expected.minusWeeks(1)
        }
        return n
    }

    private fun rub(kopecks: Long) = WeeklyTaskGenerator.rub(kopecks)
}
