package ru.my.traty.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate

class RecurringModelTest {
    private fun r(
        kopecks: Long,
        unit: PeriodUnit = PeriodUnit.MONTH,
        every: Int = 1,
        state: String = RecurringState.ACTIVE,
    ) = Recurring(
        title = "x", amountKopecks = kopecks, categoryId = 1, periodUnit = unit.key, periodEvery = every,
        nextEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(), state = state,
    )

    @Test
    fun totalsCountOnlyActivePayments() {
        val t = RecurringSummary.totals(
            listOf(
                r(39_900),                                   // 399 ₽ в месяц
                r(240_000, PeriodUnit.YEAR),                 // 2 400 ₽ в год = 200 ₽ в месяц
                r(99_900, state = RecurringState.PAUSED),    // на паузе
                r(10_000, state = RecurringState.DISMISSED), // отклонено
            )
        )
        assertEquals(2, t.count)
        assertEquals(59_900L, t.monthlyKopecks)
        assertEquals(478_800L + 240_000L, t.yearlyKopecks)
    }

    @Test
    fun emptyTotalsAreZero() {
        assertEquals(RecurringTotals(0, 0, 0), RecurringSummary.totals(emptyList()))
    }

    @Test
    fun periodAndNextDateAreRestoredFromRow() {
        val row = r(10_000, PeriodUnit.DAY, every = 45)
        assertEquals(RecurrencePeriod(PeriodUnit.DAY, 45), row.period())
        assertEquals(LocalDate.of(2026, 12, 5), row.nextDate())
        assertEquals(10_000L * 365 / 45, row.yearlyKopecks)
    }

    @Test
    fun brokenEveryIsTreatedAsOne() {
        assertEquals(RecurrencePeriod(PeriodUnit.MONTH, 1), r(100, every = 0).period())
    }

    @Test
    fun acceptedCandidateGetsNearestFutureDate() {
        val candidate = RecurringCandidate(
            key = "8|яндекс плюс", title = "Яндекс Плюс", place = "Яндекс Плюс", categoryId = 8, subcategoryId = null,
            amountKopecks = 39_900, period = RecurrencePeriod.MONTHLY, charges = 3,
            lastDate = LocalDate.of(2026, 9, 5), nextDate = LocalDate.of(2026, 10, 5), expenseIds = listOf(1, 2, 3),
        )
        // сегодня 14 ноября: 5 октября и 5 ноября уже прошли
        val item = candidate.toRecurring(LocalDate.of(2026, 11, 14))
        assertEquals(LocalDate.of(2026, 12, 5), item.nextDate())
        assertEquals(RecurringState.ACTIVE, item.state)
        assertTrue(item.autoDetected)
        assertEquals("8|яндекс плюс", item.detectKey)
        assertEquals("month", item.periodUnit)

        assertEquals(RecurringState.DISMISSED, candidate.toRecurring(LocalDate.of(2026, 11, 14), RecurringState.DISMISSED).state)
    }
}
