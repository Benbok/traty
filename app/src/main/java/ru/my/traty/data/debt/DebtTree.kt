package ru.my.traty.data.debt

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Статус платежа в дереве/графике. */
enum class TreePaymentState {
    /** Оплачен полностью. */
    PAID,

    /** Оплачен частично (внесена часть суммы). */
    PARTIALLY_PAID,

    /** Просрочен / пропущен (дата платежа наступила или прошла, платёж не внесён). */
    OVERDUE,

    /** Предстоящий платёж по графику. */
    UPCOMING,
}

/**
 * Один узел дерева платежей покупки частями / рассрочки / кредита.
 * [number] — порядковый номер платежа (начиная с 1).
 * [scheduledDate] — плановая дата по графику (null для аванса без графика).
 * [actualDate] — дата фактической оплаты (если оплачен).
 * [principalKopecks] — тело долга в копейках.
 * [interestKopecks] — проценты в копейках (для кредита).
 * [paidAmountKopecks] — сколько фактически оплачено по этому узлу.
 * [state] — статус ([TreePaymentState]).
 * [expenseId] — id связанной траты в базе (для перехода к деталям траты).
 * [daysOverdue] — количество дней просрочки (если статус [TreePaymentState.OVERDUE]).
 */
data class DebtTreeNode(
    val id: String,
    val number: Int?,
    val totalCount: Int?,
    val scheduledDate: LocalDate?,
    val actualDate: LocalDate?,
    val principalKopecks: Long,
    val interestKopecks: Long = 0L,
    val paidAmountKopecks: Long = 0L,
    val state: TreePaymentState,
    val expenseId: Long? = null,
    val daysOverdue: Long = 0L,
    val note: String? = null,
) {
    val totalKopecks: Long get() = principalKopecks + interestKopecks
    val remainingKopecks: Long get() = (totalKopecks - paidAmountKopecks).coerceAtLeast(0L)
}

/**
 * Сводка по дереву платежей: сколько оплачено, сколько просрочено, сколько предстоит.
 */
data class DebtTreeSummary(
    val totalCount: Int,
    val paidCount: Int,
    val overdueCount: Int,
    val upcomingCount: Int,
    val totalKopecks: Long?,
    val paidKopecks: Long,
    val remainingKopecks: Long?,
    val overdueKopecks: Long,
    val plannedOverpaymentKopecks: Long,
    val paidInterestKopecks: Long,
    val progressPercent: Float,
    val isPaidOff: Boolean,
)

/**
 * Полное дерево платежей по покупке частями (рассрочке, кредиту, авансу с доплатами).
 */
data class DebtTree(
    val debt: Debt,
    val summary: DebtTreeSummary,
    val nodes: List<DebtTreeNode>,
) {
    companion object {
        fun build(
            debt: Debt,
            payments: List<DebtPaymentRow>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): DebtTree {
            val principalPayments = payments.filter { it.part == PaymentPart.PRINCIPAL }
            val interestPayments = payments.filter { it.part == PaymentPart.INTEREST }
            val totalPaidPrincipal = principalPayments.sumOf { it.amountKopecks }
            val totalPaidInterest = interestPayments.sumOf { it.amountKopecks }
            val totalPaid = totalPaidPrincipal + totalPaidInterest

            val schedule = DebtSchedule.of(debt)
            return if (schedule.isNotEmpty()) {
                buildScheduledTree(
                    debt = debt,
                    schedule = schedule,
                    payments = payments,
                    totalPaidPrincipal = totalPaidPrincipal,
                    totalPaidInterest = totalPaidInterest,
                    today = today,
                    zone = zone,
                )
            } else {
                buildUnscheduledTree(
                    debt = debt,
                    payments = payments,
                    totalPaidPrincipal = totalPaidPrincipal,
                    totalPaidInterest = totalPaidInterest,
                    zone = zone,
                )
            }
        }

        private fun buildScheduledTree(
            debt: Debt,
            schedule: List<ScheduledPayment>,
            payments: List<DebtPaymentRow>,
            totalPaidPrincipal: Long,
            totalPaidInterest: Long,
            today: LocalDate,
            zone: ZoneId,
        ): DebtTree {
            val totalPaid = totalPaidPrincipal + totalPaidInterest
            val nodes = mutableListOf<DebtTreeNode>()
            var cumulativePrincipal = 0L
            var overdueAmount = 0L

            val actualPrincipalRows = payments.filter { it.part == PaymentPart.PRINCIPAL }
            var actualIndex = 0

            for (item in schedule) {
                val before = cumulativePrincipal
                cumulativePrincipal += item.principalKopecks
                val itemTargetDate = item.date

                val covered = (totalPaidPrincipal - before).coerceIn(0L, item.principalKopecks)
                val isFullyPaid = covered >= item.principalKopecks
                val isPartiallyPaid = covered > 0L && !isFullyPaid
                val isPast = itemTargetDate.isBefore(today)

                val state = when {
                    isFullyPaid -> TreePaymentState.PAID
                    isPartiallyPaid -> if (isPast) TreePaymentState.OVERDUE else TreePaymentState.PARTIALLY_PAID
                    isPast -> TreePaymentState.OVERDUE
                    else -> TreePaymentState.UPCOMING
                }

                val daysOverdue = if (state == TreePaymentState.OVERDUE) {
                    ChronoUnit.DAYS.between(itemTargetDate, today).coerceAtLeast(0L)
                } else 0L

                val unpaidPrincipal = item.principalKopecks - covered
                if (state == TreePaymentState.OVERDUE) {
                    overdueAmount += unpaidPrincipal + item.interestKopecks
                }

                val matchedActual = if (covered > 0L && actualIndex < actualPrincipalRows.size) {
                    val row = actualPrincipalRows[actualIndex]
                    if (isFullyPaid) actualIndex++
                    row
                } else null

                val actualDate = matchedActual?.let {
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                }

                nodes += DebtTreeNode(
                    id = "scheduled_${item.number}",
                    number = item.number,
                    totalCount = schedule.size,
                    scheduledDate = itemTargetDate,
                    actualDate = actualDate,
                    principalKopecks = item.principalKopecks,
                    interestKopecks = item.interestKopecks,
                    paidAmountKopecks = covered + (if (isFullyPaid) item.interestKopecks else 0L),
                    state = state,
                    expenseId = matchedActual?.expenseId,
                    daysOverdue = daysOverdue,
                )
            }

            val totalCost = debt.totalKopecks
            val remainingPrincipal = totalCost?.let { (it - totalPaidPrincipal).coerceAtLeast(0L) }
            val isPaidOff = (remainingPrincipal == 0L || debt.state == DebtState.CLOSED) && totalCost != null

            val summary = DebtTreeSummary(
                totalCount = nodes.size,
                paidCount = nodes.count { it.state == TreePaymentState.PAID },
                overdueCount = nodes.count { it.state == TreePaymentState.OVERDUE },
                upcomingCount = nodes.count { it.state == TreePaymentState.UPCOMING || it.state == TreePaymentState.PARTIALLY_PAID },
                totalKopecks = totalCost,
                paidKopecks = totalPaid,
                remainingKopecks = remainingPrincipal,
                overdueKopecks = overdueAmount,
                plannedOverpaymentKopecks = schedule.sumOf { it.interestKopecks },
                paidInterestKopecks = totalPaidInterest,
                progressPercent = if (totalCost != null && totalCost > 0) {
                    (totalPaidPrincipal.toFloat() / totalCost.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                isPaidOff = isPaidOff,
            )

            return DebtTree(debt = debt, summary = summary, nodes = nodes)
        }

        private fun buildUnscheduledTree(
            debt: Debt,
            payments: List<DebtPaymentRow>,
            totalPaidPrincipal: Long,
            totalPaidInterest: Long,
            zone: ZoneId,
        ): DebtTree {
            val totalPaid = totalPaidPrincipal + totalPaidInterest
            val nodes = mutableListOf<DebtTreeNode>()

            for ((index, row) in payments.withIndex()) {
                val date = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
                val isPrincipal = row.part == PaymentPart.PRINCIPAL
                nodes += DebtTreeNode(
                    id = "actual_${row.expenseId}_${row.part}",
                    number = index + 1,
                    totalCount = null,
                    scheduledDate = date,
                    actualDate = date,
                    principalKopecks = if (isPrincipal) row.amountKopecks else 0L,
                    interestKopecks = if (!isPrincipal) row.amountKopecks else 0L,
                    paidAmountKopecks = row.amountKopecks,
                    state = TreePaymentState.PAID,
                    expenseId = row.expenseId,
                    note = if (index == 0) "Аванс" else "Доплата",
                )
            }

            val totalCost = debt.totalKopecks
            val remaining = totalCost?.let { (it - totalPaidPrincipal).coerceAtLeast(0L) }
            if (remaining != null && remaining > 0 && debt.state == DebtState.ACTIVE) {
                nodes += DebtTreeNode(
                    id = "remainder",
                    number = nodes.size + 1,
                    totalCount = null,
                    scheduledDate = null,
                    actualDate = null,
                    principalKopecks = remaining,
                    interestKopecks = 0L,
                    paidAmountKopecks = 0L,
                    state = TreePaymentState.UPCOMING,
                    note = "Остаток к доплате",
                )
            }

            val isPaidOff = (remaining == 0L || debt.state == DebtState.CLOSED) && totalCost != null

            val summary = DebtTreeSummary(
                totalCount = nodes.size,
                paidCount = nodes.count { it.state == TreePaymentState.PAID },
                overdueCount = 0,
                upcomingCount = nodes.count { it.state == TreePaymentState.UPCOMING },
                totalKopecks = totalCost,
                paidKopecks = totalPaid,
                remainingKopecks = remaining,
                overdueKopecks = 0L,
                plannedOverpaymentKopecks = 0L,
                paidInterestKopecks = totalPaidInterest,
                progressPercent = if (totalCost != null && totalCost > 0) {
                    (totalPaidPrincipal.toFloat() / totalCost.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                isPaidOff = isPaidOff,
            )

            return DebtTree(debt = debt, summary = summary, nodes = nodes)
        }
    }
}
