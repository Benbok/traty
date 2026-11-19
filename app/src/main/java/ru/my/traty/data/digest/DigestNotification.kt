package ru.my.traty.data.digest

import ru.my.traty.ui.formatRub

internal data class DigestGoalReminder(val title: String, val remainingKopecks: Long)

internal data class DigestNotification(val title: String, val body: String, val opensPurchaseGoals: Boolean)

internal fun digestNotification(
    digest: DailyDigest?,
    goals: List<DigestGoalReminder>,
): DigestNotification? {
    val due = goals.filter { it.remainingKopecks > 0 }
    val reminder = when (due.size) {
        0 -> null
        1 -> due.single().let { "На покупку «${it.title}» сегодня осталось отложить ${formatRub(it.remainingKopecks)}." }
        else -> {
            val total = due.fold(0L) { sum, goal -> sum + goal.remainingKopecks.coerceAtMost(Long.MAX_VALUE - sum) }
            "На покупки сегодня осталось отложить ${formatRub(total)}."
        }
    }
    if (digest == null && reminder == null) return null
    return DigestNotification(
        title = digest?.title ?: "Накопления на покупки",
        body = listOfNotNull(reminder, digest?.body).joinToString("\n"),
        opensPurchaseGoals = reminder != null,
    )
}
