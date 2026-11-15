package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtRemindersTest {
    private val first = LocalDate.of(2026, 11, 15)
    private val phone = Debt(
        id = 1, title = "Телефон", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = first.toEpochDay(),
    )
    private fun paid(vararg amounts: Long) = amounts.map { PaidPart(first.toEpochDay(), PaymentPart.PRINCIPAL, it) }
    private fun due(today: LocalDate, debt: Debt = phone, paid: List<PaidPart> = emptyList(), notified: Map<Long, Long> = emptyMap()) =
        DebtReminders.due(listOf(debt to paid), today, notified)

    @Test
    fun dayBeforeAndOnTheDay() {
        assertTrue(due(first.minusDays(2)).isEmpty())
        val before = due(first.minusDays(1)).single()
        assertEquals(1L, before.daysLeft)
        assertEquals(1_000_000L, before.amountKopecks)
        assertEquals("Завтра платёж", DebtReminders.title(before.daysLeft))
        assertEquals(0L, due(first).single().daysLeft)
    }

    @Test
    fun overdueRemindsThreeDaysThenStops() {
        assertEquals(-3L, due(first.plusDays(3)).single().daysLeft)
        assertTrue(due(first.plusDays(4)).isEmpty())
    }

    @Test
    fun paidPaymentMovesReminderToNextOne() {
        // Первый платёж внесён: ближайший — второй, через месяц
        assertTrue(due(first, paid = paid(1_000_000)).isEmpty())
        val next = due(first.plusMonths(1), paid = paid(1_000_000)).single()
        assertEquals(2, next.number)
        assertEquals(6, next.count)
    }

    @Test
    fun onceADayAndNotForClosedOrCancelled() {
        assertTrue(due(first, notified = mapOf(1L to first.toEpochDay())).isEmpty())
        assertEquals(1, due(first, notified = mapOf(1L to first.toEpochDay() - 1)).size)
        assertTrue(due(first, debt = phone.copy(state = DebtState.CLOSED)).isEmpty())
        assertTrue(due(first, debt = phone.copy(state = DebtState.CANCELLED)).isEmpty())
    }

    @Test
    fun advanceWithoutScheduleNeverReminds() {
        val fridge = Debt(id = 2, title = "Холодильник", categoryId = 1, totalKopecks = 5_000_000)
        assertTrue(due(first, debt = fridge).isEmpty())
    }

    @Test
    fun loanReminderIncludesInterest() {
        val loan = Debt(
            id = 3, title = "Ноутбук", categoryId = 1, kind = DebtKind.LOAN.key, rateBasisPoints = 2000,
            totalKopecks = 8_000_000, paymentsCount = 12, firstDueEpochDay = first.toEpochDay(),
        )
        assertEquals(607_743L + 133_333L, due(first, debt = loan).single().amountKopecks)
    }
}
