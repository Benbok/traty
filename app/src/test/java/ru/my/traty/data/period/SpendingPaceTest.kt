package ru.my.traty.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SpendingPaceTest {
    private val start = LocalDate.of(2026, 11, 10)
    private val period = BillingPeriod(start, LocalDate.of(2026, 12, 9)) // 30 дней

    @Test
    fun forecastByCurrentPace() {
        // 5 дней прошло (10–14), потрачено 10 000 ₽ → 2 000 ₽ в день → 60 000 ₽ к концу
        val p = SpendingPaces.of(period, LocalDate.of(2026, 11, 14), 10_000_00)!!
        assertEquals(5, p.daysPassed)
        assertEquals(30, p.daysTotal)
        assertEquals(25, p.daysLeft)
        assertEquals(2_000_00, p.dailyAverage)
        assertEquals(60_000_00L, p.forecast)
    }

    @Test
    fun firstDayCountsAsOneDay() {
        val p = SpendingPaces.of(period, start, 300_00)!!
        assertEquals(1, p.daysPassed)
        assertEquals(9_000_00L, p.forecast)
    }

    @Test
    fun lastDayForecastIsWhatWasSpent() {
        val p = SpendingPaces.of(period, period.endDate, 45_000_00)!!
        assertEquals(0, p.daysLeft)
        assertEquals(45_000_00L, p.forecast)
        assertEquals(1_500_00, p.dailyAverage)
    }

    @Test
    fun finishedPeriodHasAverageButNoForecast() {
        val p = SpendingPaces.of(period, LocalDate.of(2027, 1, 1), 30_000_00)!!
        assertEquals(30, p.daysPassed)
        assertEquals(1_000_00, p.dailyAverage)
        assertNull(p.forecast)
    }

    @Test
    fun futurePeriodHasNoPace() {
        assertNull(SpendingPaces.of(period, start.minusDays(1), 0))
    }

    @Test
    fun nothingSpentForecastsZero() {
        assertEquals(0L, SpendingPaces.of(period, LocalDate.of(2026, 11, 20), 0)!!.forecast)
    }
}
