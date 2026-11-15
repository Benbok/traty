package ru.my.traty.data.weekly

/** Тексты итогов заданий: для уведомления в воскресенье и для экрана. */
object WeeklyTaskTexts {
    /** «Итог недели: 2 из 3» и строки по заданиям. */
    fun weekResult(progress: List<TaskProgress>): Pair<String, String> {
        val done = progress.filter { it.state == TaskState.DONE }
        val saved = done.sumOf { it.savedKopecks }
        val title = "Итог недели: ${done.size} из ${progress.size}" +
            if (saved > 0) ", сэкономлено ${WeeklyTaskGenerator.rub(saved)}" else ""
        val body = progress.joinToString("\n") { p ->
            val mark = when (p.state) {
                TaskState.DONE -> "✅"
                TaskState.FAILED -> "❌"
                TaskState.IN_PROGRESS -> "⏳"
            }
            "$mark ${p.task.title}"
        }
        return title to body
    }

    /** «1 неделя», «3 недели», «5 недель». */
    fun weeksWord(n: Int): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> "недель"
            m10 == 1 -> "неделя"
            m10 in 2..4 -> "недели"
            else -> "недель"
        }
    }

    /** Своё задание: формулировка по виду, категории и цели. */
    fun customTask(weekStart: java.time.LocalDate, type: WeeklyTaskType, categoryId: Long?, categoryName: String, target: Long): WeeklyTask {
        val title = when (type) {
            WeeklyTaskType.CATEGORY_LIMIT -> "«$categoryName» — не больше ${WeeklyTaskGenerator.rub(target)}"
            WeeklyTaskType.NO_SPEND_DAYS -> "«$categoryName» — $target ${WeeklyTaskGenerator.daysWord(target)} без трат"
            WeeklyTaskType.COUNT_LIMIT -> "«$categoryName» — не больше $target ${WeeklyTaskGenerator.timesWord(target)}"
            WeeklyTaskType.REVIEW_SUBSCRIPTIONS -> "Пересмотреть подписки"
        }
        return WeeklyTask(
            weekStart = Weeks.monday(weekStart).toEpochDay(), type = type.name,
            categoryId = categoryId.takeIf { type != WeeklyTaskType.REVIEW_SUBSCRIPTIONS },
            title = title, target = target, custom = true,
        )
    }
}
