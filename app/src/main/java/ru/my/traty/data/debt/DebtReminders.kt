package ru.my.traty.data.debt

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Платёж по рассрочке или кредиту, о котором пора напомнить. [daysLeft] < 0 — платёж просрочен. */
data class DebtReminder(
    val debtId: Long,
    val title: String,
    val number: Int,
    val count: Int,
    val date: LocalDate,
    val amountKopecks: Long,
    val daysLeft: Long,
)

/**
 * Какие напоминания показать сегодня. Напоминаем за [daysBefore] дней до платежа и в сам день, а если платёж
 * не внесён — ещё [maxOverdueDays] дней после срока, дальше молчим (платёж виден в приложении просроченным).
 * В одни сутки не больше одного уведомления о покупке: [notified] — id покупки → последний день напоминания.
 */
object DebtReminders {
    fun due(
        items: List<Pair<Debt, List<PaidPart>>>,
        today: LocalDate,
        notified: Map<Long, Long>,
        daysBefore: Int = 1,
        maxOverdueDays: Int = 3,
    ): List<DebtReminder> = items.mapNotNull { (debt, paid) ->
        if (debt.state != DebtState.ACTIVE || notified[debt.id] == today.toEpochDay()) return@mapNotNull null
        val next = DebtProgress.of(debt, paid, today).next ?: return@mapNotNull null
        val daysLeft = ChronoUnit.DAYS.between(today, next.date)
        if (daysLeft > daysBefore || -daysLeft > maxOverdueDays) return@mapNotNull null
        DebtReminder(debt.id, debt.title, next.number, next.count, next.date, next.totalKopecks, daysLeft)
    }.sortedBy { it.date }

    fun title(daysLeft: Long): String = when {
        daysLeft < 0 -> "Платёж просрочен на ${-daysLeft} дн."
        daysLeft == 0L -> "Сегодня платёж"
        daysLeft == 1L -> "Завтра платёж"
        else -> "Через $daysLeft дн. платёж"
    }
}
