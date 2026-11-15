package ru.my.traty.data.budget

import ru.my.traty.data.Recurring
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtProgress
import ru.my.traty.data.debt.PaidPart
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.recurring.RecurringSchedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Платёж, который ещё предстоит в этом периоде. [overdue] — срок уже прошёл, а платёж не внесён. */
data class PromisedItem(
    val title: String,
    val date: LocalDate,
    val amountKopecks: Long,
    val fromDebt: Boolean,
    val approximate: Boolean = false,
    val overdue: Boolean = false,
)

/** «Уже обещано»: что ещё спишется до конца периода по рассрочкам, кредитам и подпискам. */
data class Promised(val items: List<PromisedItem> = emptyList()) {
    val totalKopecks: Long get() = items.sumOf { it.amountKopecks }
    val approximate: Boolean get() = items.any { it.approximate }
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Считается только для текущего периода: у будущего периода обещанное зависит от того, что успеют
 * оплатить до него, а у прошедшего ничего не предстоит. Просроченные неоплаченные платежи входят:
 * их всё равно нужно внести.
 */
object PromisedPayments {
    fun of(
        debts: List<Pair<Debt, List<PaidPart>>>,
        recurring: List<Recurring>,
        period: BillingPeriod,
        today: LocalDate,
    ): Promised {
        if (today !in period) return Promised()
        val items = debtItems(debts, period, today) + recurringItems(recurring, period, today)
        return Promised(items.sortedWith(compareBy({ it.date }, { it.title })))
    }

    private fun debtItems(debts: List<Pair<Debt, List<PaidPart>>>, period: BillingPeriod, today: LocalDate): List<PromisedItem> =
        debts.mapNotNull { (debt, paid) ->
            val amount = DebtProgress.dueBetween(debt, paid, today, period.endDate)
            val next = DebtProgress.of(debt, paid, today).next
            if (amount <= 0 || next == null) null
            else PromisedItem(debt.title, next.date, amount, fromDebt = true, overdue = next.overdue)
        }

    private fun recurringItems(recurring: List<Recurring>, period: BillingPeriod, today: LocalDate): List<PromisedItem> {
        val days = ChronoUnit.DAYS.between(today, period.endDate).toInt()
        return RecurringSchedule.upcoming(recurring, today, days).map {
            PromisedItem(
                title = it.item.title,
                date = it.date,
                amountKopecks = it.item.amountKopecks,
                fromDebt = false,
                approximate = it.item.amountVaries,
                overdue = it.overdue,
            )
        }
    }
}
