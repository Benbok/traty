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
 * [interestKopecks] — проценты по графику в копейках (для кредита).
 * [paidAmountKopecks] — сколько фактически оплачено по этому узлу: тело и уплаченные проценты.
 * [state] — статус ([TreePaymentState]).
 * [expenseIds] — траты, из которых сложился платёж (их может быть несколько: платёж внесли частями),
 * [expenseId] — последняя из них, для перехода к деталям траты.
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
    val expenseIds: List<Long> = emptyList(),
    val expenseId: Long? = expenseIds.lastOrNull(),
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
 *
 * Итоги (оплачено, остаток, переплата) берутся из [DebtProgress], как в списке покупок, вопросе
 * «Доплата?» и ответах ассистента, чтобы цифры везде совпадали.
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
            val status = DebtProgress.of(debt, payments.map { it.toPaidPart(zone) }, today)
            val schedule = DebtSchedule.of(debt)
            val nodes = if (schedule.isNotEmpty()) scheduledNodes(debt, schedule, payments, today, zone)
            else unscheduledNodes(debt, payments, status, zone)
            return DebtTree(debt, summaryOf(debt, status, nodes), nodes)
        }

        /** Что пришлось на одну строку графика. */
        private class Slot(val item: ScheduledPayment) {
            var principal = 0L
            var interest = 0L
            var completedOn: LocalDate? = null
            val expenseIds = mutableListOf<Long>()
            fun add(expenseId: Long) { if (expenseId !in expenseIds) expenseIds += expenseId }
        }

        /**
         * Каждая трата раскладывается по строкам графика по сумме: платёж двумя частями ложится в одну строку,
         * двойной платёж закрывает две. Проценты кредита идут в строку, которая была ближайшей в момент
         * оплаты (так их и считает [DebtRepository]).
         */
        private fun scheduledNodes(
            debt: Debt,
            schedule: List<ScheduledPayment>,
            payments: List<DebtPaymentRow>,
            today: LocalDate,
            zone: ZoneId,
        ): List<DebtTreeNode> {
            val slots = schedule.map { Slot(it) }
            var current = 0
            // Проценты того же платежа записаны с тем же временем: их раскладываем раньше тела
            val ordered = payments.sortedWith(compareBy({ it.timestamp }, { if (it.part == PaymentPart.INTEREST) 0 else 1 }))
            for (row in ordered) {
                val day = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
                if (row.part == PaymentPart.INTEREST) {
                    val slot = slots[current.coerceAtMost(slots.lastIndex)]
                    slot.interest += row.amountKopecks
                    slot.add(row.expenseId)
                    continue
                }
                var left = row.amountKopecks
                while (left > 0 && current < slots.size) {
                    val slot = slots[current]
                    val take = minOf(left, slot.item.principalKopecks - slot.principal)
                    slot.principal += take
                    slot.add(row.expenseId)
                    left -= take
                    if (slot.principal >= slot.item.principalKopecks) {
                        slot.completedOn = day
                        current++
                    }
                }
                // Заплатили больше цены — лишнее остаётся у последней строки
                if (left > 0) slots.last().let { it.principal += left; it.add(row.expenseId) }
            }
            val active = debt.state == DebtState.ACTIVE
            return slots.mapNotNull { slot -> nodeOf(slot, schedule.size, today, active) }
        }

        /** У закрытой и отменённой покупки неоплаченные строки не нужны: их уже не ждут. */
        private fun nodeOf(slot: Slot, count: Int, today: LocalDate, active: Boolean): DebtTreeNode? {
            val item = slot.item
            val fullyPaid = slot.principal >= item.principalKopecks
            val started = slot.principal > 0 || slot.interest > 0
            val past = item.date.isBefore(today)
            val state = when {
                fullyPaid -> TreePaymentState.PAID
                !active && !started -> return null
                !active -> TreePaymentState.PARTIALLY_PAID
                past -> TreePaymentState.OVERDUE
                started -> TreePaymentState.PARTIALLY_PAID
                else -> TreePaymentState.UPCOMING
            }
            return DebtTreeNode(
                id = "scheduled_${item.number}",
                number = item.number,
                totalCount = count,
                scheduledDate = item.date,
                actualDate = slot.completedOn,
                principalKopecks = item.principalKopecks,
                interestKopecks = item.interestKopecks,
                paidAmountKopecks = minOf(slot.principal, item.principalKopecks) + minOf(slot.interest, item.interestKopecks),
                state = state,
                expenseIds = slot.expenseIds.toList(),
                daysOverdue = if (state == TreePaymentState.OVERDUE) ChronoUnit.DAYS.between(item.date, today) else 0L,
            )
        }

        /** Аванс и доплаты без графика: каждая трата — свой узел, в конце остаток. */
        private fun unscheduledNodes(debt: Debt, payments: List<DebtPaymentRow>, status: DebtStatus, zone: ZoneId): List<DebtTreeNode> {
            val nodes = payments.sortedBy { it.timestamp }.mapIndexed { index, row ->
                val date = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
                val isPrincipal = row.part == PaymentPart.PRINCIPAL
                DebtTreeNode(
                    id = "actual_${row.expenseId}_${row.part}",
                    number = index + 1,
                    totalCount = null,
                    scheduledDate = date,
                    actualDate = date,
                    principalKopecks = if (isPrincipal) row.amountKopecks else 0L,
                    interestKopecks = if (!isPrincipal) row.amountKopecks else 0L,
                    paidAmountKopecks = row.amountKopecks,
                    state = TreePaymentState.PAID,
                    expenseIds = listOf(row.expenseId),
                    note = if (index == 0) "Аванс" else "Доплата",
                )
            }
            val remaining = status.remainingKopecks
            if (remaining == null || remaining <= 0 || debt.state != DebtState.ACTIVE) return nodes
            return nodes + DebtTreeNode(
                id = "remainder",
                number = nodes.size + 1,
                totalCount = null,
                scheduledDate = null,
                actualDate = null,
                principalKopecks = remaining,
                state = TreePaymentState.UPCOMING,
                note = "Остаток к доплате",
            )
        }

        private fun summaryOf(debt: Debt, status: DebtStatus, nodes: List<DebtTreeNode>): DebtTreeSummary {
            val total = debt.totalKopecks
            val overdue = nodes.filter { it.state == TreePaymentState.OVERDUE }
            return DebtTreeSummary(
                totalCount = nodes.size,
                paidCount = nodes.count { it.state == TreePaymentState.PAID },
                overdueCount = overdue.size,
                upcomingCount = nodes.count { it.state == TreePaymentState.UPCOMING || it.state == TreePaymentState.PARTIALLY_PAID },
                totalKopecks = total,
                paidKopecks = status.paidKopecks,
                remainingKopecks = status.remainingKopecks,
                overdueKopecks = overdue.sumOf { it.remainingKopecks },
                plannedOverpaymentKopecks = status.plannedOverpaymentKopecks,
                paidInterestKopecks = status.paidInterestKopecks,
                progressPercent = if (total != null && total > 0) (status.paidPrincipalKopecks.toFloat() / total).coerceIn(0f, 1f) else 0f,
                isPaidOff = total != null && (status.isPaidOff || debt.state == DebtState.CLOSED),
            )
        }

        private fun DebtPaymentRow.toPaidPart(zone: ZoneId) =
            PaidPart(Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay(), part, amountKopecks)
    }
}
