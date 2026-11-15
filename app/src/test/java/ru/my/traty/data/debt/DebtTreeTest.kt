package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DebtTreeTest {
    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 11, 15)
    private val tvInstallment = Debt(
        title = "Телевизор",
        categoryId = 1,
        kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, // 60 000 руб
        paymentsCount = 6,        // 6 платежей по 10 000 руб
        firstDueEpochDay = start.toEpochDay(),
    )

    private fun row(
        debtId: Long = 1,
        expenseId: Long,
        kopecks: Long,
        day: LocalDate,
        part: String = PaymentPart.PRINCIPAL,
    ) = DebtPaymentRow(
        debtId = debtId,
        expenseId = expenseId,
        part = part,
        amountKopecks = kopecks,
        timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun installmentTreeInitialState() {
        // Ни одного платежа ещё не внесено, сегодня — дата первого платежа
        val tree = DebtTree.build(tvInstallment, emptyList(), today = start, zone = zone)
        assertEquals(6, tree.summary.totalCount)
        assertEquals(0, tree.summary.paidCount)
        assertEquals(0, tree.summary.overdueCount)
        assertEquals(6, tree.summary.upcomingCount)
        assertEquals(6_000_000L, tree.summary.totalKopecks)
        assertEquals(0L, tree.summary.paidKopecks)
        assertEquals(6_000_000L, tree.summary.remainingKopecks)
        assertEquals(0f, tree.summary.progressPercent, 0.001f)
        assertFalse(tree.summary.isPaidOff)

        // Первый платёж: сегодня, не просрочен
        val first = tree.nodes.first()
        assertEquals(1, first.number)
        assertEquals(TreePaymentState.UPCOMING, first.state)
        assertEquals(1_000_000L, first.principalKopecks)
        assertEquals(0L, first.daysOverdue)
    }

    @Test
    fun installmentTreeWithOverdueAndPaidPayments() {
        // Оплачен 1-й платёж вовремя (15 ноября)
        val p1 = row(expenseId = 10, kopecks = 1_000_000, day = start)
        // Сегодня 20 января 2027:
        // 1-й платёж (15 ноя) — ОПЛАЧЕН
        // 2-й платёж (15 дек) — ПРОСРОЧЕН (срок прошёл, не оплачен)
        // 3-й платёж (15 янв) — ПРОСРОЧЕН (срок прошёл 5 дней назад)
        // 4, 5, 6 платежи (фев, мар, апр) — ПРЕДСТОЯЩИЕ
        val today = LocalDate.of(2027, 1, 20)
        val tree = DebtTree.build(tvInstallment, listOf(p1), today = today, zone = zone)

        assertEquals(6, tree.summary.totalCount)
        assertEquals(1, tree.summary.paidCount)
        assertEquals(2, tree.summary.overdueCount)
        assertEquals(3, tree.summary.upcomingCount)
        assertEquals(1_000_000L, tree.summary.paidKopecks)
        assertEquals(5_000_000L, tree.summary.remainingKopecks)
        assertEquals(2_000_000L, tree.summary.overdueKopecks)
        assertEquals(1f / 6f, tree.summary.progressPercent, 0.01f)
        assertFalse(tree.summary.isPaidOff)

        val node1 = tree.nodes[0]
        assertEquals(TreePaymentState.PAID, node1.state)
        assertEquals(10L, node1.expenseId)
        assertEquals(start, node1.actualDate)

        val node2 = tree.nodes[1]
        assertEquals(TreePaymentState.OVERDUE, node2.state)
        assertTrue(node2.daysOverdue > 30)

        val node3 = tree.nodes[2]
        assertEquals(TreePaymentState.OVERDUE, node3.state)
        assertEquals(5L, node3.daysOverdue)

        val node4 = tree.nodes[3]
        assertEquals(TreePaymentState.UPCOMING, node4.state)
        assertEquals(0L, node4.daysOverdue)
    }

    @Test
    fun installmentTreeFullyPaid() {
        val payments = (0 until 6).map { i ->
            row(expenseId = 100L + i, kopecks = 1_000_000, day = start.plusMonths(i.toLong()))
        }
        val tree = DebtTree.build(tvInstallment, payments, today = start.plusMonths(6), zone = zone)
        assertEquals(6, tree.summary.paidCount)
        assertEquals(0, tree.summary.overdueCount)
        assertEquals(0, tree.summary.upcomingCount)
        assertEquals(0L, tree.summary.remainingKopecks)
        assertEquals(1f, tree.summary.progressPercent, 0.001f)
        assertTrue(tree.summary.isPaidOff)
    }

    @Test
    fun partialWithoutScheduleTree() {
        val fridge = Debt(
            title = "Холодильник",
            categoryId = 2,
            kind = DebtKind.PARTIAL.key,
            totalKopecks = 5_000_000,
        )
        val p1 = row(expenseId = 1, kopecks = 2_000_000, day = start)
        val p2 = row(expenseId = 2, kopecks = 1_000_000, day = start.plusDays(5))

        val tree = DebtTree.build(fridge, listOf(p1, p2), today = start.plusDays(10), zone = zone)
        assertEquals(3, tree.summary.totalCount) // 2 оплаченных + 1 остаток
        assertEquals(2, tree.summary.paidCount)
        assertEquals(1, tree.summary.upcomingCount)
        assertEquals(3_000_000L, tree.summary.paidKopecks)
        assertEquals(2_000_000L, tree.summary.remainingKopecks)
        assertEquals(0.6f, tree.summary.progressPercent, 0.001f)

        assertEquals("Аванс", tree.nodes[0].note)
        assertEquals("Доплата", tree.nodes[1].note)
        assertEquals("Остаток к доплате", tree.nodes[2].note)
        assertEquals(2_000_000L, tree.nodes[2].principalKopecks)
    }

    @Test
    fun loanWithInterestBreakdown() {
        val loan = Debt(
            title = "Автокредит",
            categoryId = 3,
            kind = DebtKind.LOAN.key,
            rateBasisPoints = 2000, // 20%
            totalKopecks = 8_000_000,
            paymentsCount = 12,
            firstDueEpochDay = start.toEpochDay(),
        )
        val p1Principal = row(expenseId = 1, kopecks = 607_743, day = start, part = PaymentPart.PRINCIPAL)
        val p1Interest = row(expenseId = 2, kopecks = 133_333, day = start, part = PaymentPart.INTEREST)

        val tree = DebtTree.build(loan, listOf(p1Principal, p1Interest), today = start, zone = zone)
        assertEquals(12, tree.summary.totalCount)
        assertEquals(1, tree.summary.paidCount)
        assertEquals(11, tree.summary.upcomingCount)
        assertEquals(133_333L, tree.summary.paidInterestKopecks)
        assertTrue(tree.summary.plannedOverpaymentKopecks > 0)

        val firstNode = tree.nodes[0]
        assertEquals(TreePaymentState.PAID, firstNode.state)
        assertTrue(firstNode.interestKopecks > 0)
        assertTrue(firstNode.principalKopecks > 0)
    }
}
