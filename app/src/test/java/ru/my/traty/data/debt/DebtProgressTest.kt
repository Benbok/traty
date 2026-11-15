package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtProgressTest {
    private val start = LocalDate.of(2026, 11, 15)
    private val fridge = Debt(title = "Холодильник", place = "Ozon", categoryId = 1, totalKopecks = 6_000_000)
    private val tv = Debt(
        title = "Телевизор", categoryId = 1, kind = DebtKind.INSTALLMENT.key, totalKopecks = 6_000_000,
        paymentsCount = 6, firstDueEpochDay = start.toEpochDay(),
    )

    private fun paid(day: LocalDate, kopecks: Long, part: String = PaymentPart.PRINCIPAL) = PaidPart(day.toEpochDay(), part, kopecks)

    @Test
    fun advanceAndTopUpWithoutSchedule() {
        val s = DebtProgress.of(fridge, listOf(paid(start, 2_000_000), paid(start.plusDays(7), 500_000)), start.plusDays(7))
        assertEquals(2_500_000L, s.paidKopecks)
        assertEquals(3_500_000L, s.remainingKopecks)
        assertEquals(2, s.paymentsMade)
        assertNull(s.next)
        assertFalse(s.isPaidOff)
    }

    @Test
    fun unknownPriceHasNoRemainder() {
        val s = DebtProgress.of(fridge.copy(totalKopecks = null), listOf(paid(start, 2_000_000)), start)
        assertNull(s.remainingKopecks)
        assertFalse(s.isPaidOff)
    }

    @Test
    fun nextInstallmentAfterTwoPayments() {
        val s = DebtProgress.of(tv, listOf(paid(start, 1_000_000), paid(start.plusMonths(1), 1_000_000)), start.plusMonths(1))
        val next = checkNotNull(s.next)
        assertEquals(3, next.number)
        assertEquals(6, next.count)
        assertEquals(start.plusMonths(2), next.date)
        assertEquals(1_000_000L, next.totalKopecks)
        assertFalse(next.overdue)
    }

    @Test
    fun overpaymentMovesScheduleForward() {
        val s = DebtProgress.of(tv, listOf(paid(start, 1_500_000)), start)
        assertEquals(2, checkNotNull(s.next).number)
        assertEquals(500_000L, checkNotNull(s.next).principalKopecks)
    }

    @Test
    fun missedPaymentIsOverdue() {
        val s = DebtProgress.of(tv, listOf(paid(start, 1_000_000)), start.plusMonths(1).plusDays(3))
        assertTrue(checkNotNull(s.next).overdue)
    }

    @Test
    fun fullyPaidHasNoNext() {
        val s = DebtProgress.of(tv, (0L until 6L).map { paid(start.plusMonths(it), 1_000_000) }, start.plusMonths(6))
        assertTrue(s.isPaidOff)
        assertNull(s.next)
    }

    @Test
    fun interestIsCountedSeparately() {
        val loan = tv.copy(kind = DebtKind.LOAN.key, rateBasisPoints = 2000, totalKopecks = 8_000_000, paymentsCount = 12)
        val s = DebtProgress.of(loan, listOf(paid(start, 607_743), paid(start, 133_333, PaymentPart.INTEREST)), start)
        assertEquals(607_743L, s.paidPrincipalKopecks)
        assertEquals(133_333L, s.paidInterestKopecks)
        assertEquals(1, s.paymentsMade)
        assertEquals(2, checkNotNull(s.next).number)
        assertEquals(892_912L, s.plannedOverpaymentKopecks)
    }

    @Test
    fun dueBetweenCountsUnpaidPaymentsInRange() {
        val paidOne = listOf(paid(start, 1_000_000))
        // Ноябрь оплачен, в декабре — один платёж
        assertEquals(1_000_000L, DebtProgress.dueBetween(tv, paidOne, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
        // Пропущенный декабрьский платёж не теряется в январе
        assertEquals(2_000_000L, DebtProgress.dueBetween(tv, paidOne, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)))
        assertEquals(0L, DebtProgress.dueBetween(tv.copy(state = DebtState.CANCELLED), paidOne, start, start.plusYears(1)))
    }
}
