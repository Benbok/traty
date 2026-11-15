package ru.my.traty.data.debt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DebtOrphansTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 10)
    private val createdAt = day.atTime(15, 0).toInstant(zone).toEpochMilli()

    private fun debt(kind: DebtKind = DebtKind.PARTIAL, firstDue: LocalDate? = null, state: String = DebtState.ACTIVE) = Debt(
        title = "Телевизор", place = "Ozon", categoryId = 1, kind = kind.key, totalKopecks = 5_000_000,
        paymentsCount = if (kind == DebtKind.PARTIAL) null else 6,
        firstDueEpochDay = firstDue?.toEpochDay(), state = state, createdAt = createdAt,
    )

    @Test
    fun advanceWithoutPaymentsIsOrphan() {
        // Удалили единственную трату «аванс за телевизор»: покупку больше нечем держать
        assertTrue(DebtOrphans.isOrphan(debt(), paymentsLeft = 0, zone = zone))
    }

    @Test
    fun purchaseWithPaymentsStays() {
        assertFalse(DebtOrphans.isOrphan(debt(), paymentsLeft = 1, zone = zone))
    }

    @Test
    fun installmentPaidOnPurchaseDayIsOrphanWithoutPayments() {
        assertTrue(DebtOrphans.isOrphan(debt(DebtKind.INSTALLMENT, firstDue = day), 0, zone))
    }

    @Test
    fun loanTakenWithoutPaymentStays() {
        // Взял в кредит, первый платёж через месяц — платежей ещё нет, и это нормально
        assertFalse(DebtOrphans.isOrphan(debt(DebtKind.LOAN, firstDue = day.plusMonths(1)), 0, zone))
    }

    @Test
    fun closedOrCancelledAreNotTouched() {
        assertFalse(DebtOrphans.isOrphan(debt(state = DebtState.CLOSED), 0, zone))
        assertFalse(DebtOrphans.isOrphan(debt(state = DebtState.CANCELLED), 0, zone))
    }
}
