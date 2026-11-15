package ru.my.traty.data.debt

import java.time.LocalDate

/** Ближайший неоплаченный платёж по графику. Суммы в копейках. */
data class NextPayment(
    val number: Int,
    val count: Int,
    val date: LocalDate,
    val principalKopecks: Long,
    val interestKopecks: Long,
    val overdue: Boolean,
) {
    val totalKopecks: Long get() = principalKopecks + interestKopecks
}

/**
 * Состояние покупки на сегодня: сколько оплачено, сколько осталось, какой платёж следующий.
 * [remainingKopecks] — остаток цены (тела); null, если цена неизвестна.
 * [plannedOverpaymentKopecks] — проценты по всему графику; [paidInterestKopecks] — уже уплаченные.
 */
data class DebtStatus(
    val paidPrincipalKopecks: Long,
    val paidInterestKopecks: Long,
    val paymentsMade: Int,
    val remainingKopecks: Long?,
    val next: NextPayment?,
    val plannedOverpaymentKopecks: Long,
) {
    val paidKopecks: Long get() = paidPrincipalKopecks + paidInterestKopecks
    val isPaidOff: Boolean get() = remainingKopecks == 0L
}

/**
 * Расчёт по покупке из её платежей. Платежи по графику засчитываются по порядку:
 * сколько тела оплачено, столько первых платежей и считается закрытыми, даже если платили другими суммами
 * (доплатили больше — следующий платёж ближе к концу графика).
 */
object DebtProgress {
    fun of(debt: Debt, paid: List<PaidPart>, today: LocalDate): DebtStatus {
        val principal = paid.filter { it.part == PaymentPart.PRINCIPAL }
        val paidPrincipal = principal.sumOf { it.amountKopecks }
        val paidInterest = paid.filter { it.part == PaymentPart.INTEREST }.sumOf { it.amountKopecks }
        val remaining = debt.totalKopecks?.let { (it - paidPrincipal).coerceAtLeast(0) }
        val schedule = DebtSchedule.of(debt)
        val next = if (remaining == 0L || debt.state != DebtState.ACTIVE) null else nextPayment(schedule, paidPrincipal, today)
        return DebtStatus(
            paidPrincipalKopecks = paidPrincipal,
            paidInterestKopecks = paidInterest,
            paymentsMade = principal.map { it.epochDay }.distinct().size,
            remainingKopecks = remaining,
            next = next,
            plannedOverpaymentKopecks = schedule.sumOf { it.interestKopecks },
        )
    }

    /**
     * Сколько ещё предстоит заплатить по графику с [from] по [to] включительно — «уже обещано» в плане трат.
     * Просроченные неоплаченные платежи считаются в первый день, чтобы их не потерять.
     */
    fun dueBetween(debt: Debt, paid: List<PaidPart>, from: LocalDate, to: LocalDate): Long {
        if (debt.state != DebtState.ACTIVE || from.isAfter(to)) return 0
        val paidPrincipal = paid.filter { it.part == PaymentPart.PRINCIPAL }.sumOf { it.amountKopecks }
        var cumulative = 0L
        var sum = 0L
        for (item in DebtSchedule.of(debt)) {
            val before = cumulative
            cumulative += item.principalKopecks
            if (cumulative <= paidPrincipal) continue
            if (item.date.isAfter(to)) break
            val unpaidPrincipal = cumulative - maxOf(before, paidPrincipal)
            sum += unpaidPrincipal + item.interestKopecks
        }
        return sum
    }

    private fun nextPayment(schedule: List<ScheduledPayment>, paidPrincipal: Long, today: LocalDate): NextPayment? {
        var cumulative = 0L
        for (item in schedule) {
            val before = cumulative
            cumulative += item.principalKopecks
            if (cumulative <= paidPrincipal) continue
            return NextPayment(
                number = item.number,
                count = schedule.size,
                date = item.date,
                principalKopecks = cumulative - maxOf(before, paidPrincipal),
                interestKopecks = item.interestKopecks,
                overdue = item.date.isBefore(today),
            )
        }
        return null
    }
}
