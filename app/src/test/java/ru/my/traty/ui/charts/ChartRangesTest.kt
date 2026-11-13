package ru.my.traty.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ui.charts.model.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId

class ChartRangesTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun customRangeIncludesBothEndDates() {
        val r = customChartRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 9), zone)
        assertEquals(LocalDate.of(2026, 10, 12), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), r.endDate)
        assertEquals(millis(LocalDate.of(2026, 10, 12)), r.startMillis)
        // «по 9 ноября» включительно: верхняя граница — начало 10 ноября
        assertEquals(millis(LocalDate.of(2026, 11, 10)), r.endMillis)
    }

    @Test
    fun customRangeSwapsReversedDates() {
        val r = customChartRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 12), zone)
        assertEquals(LocalDate.of(2026, 10, 12), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), r.endDate)
    }

    @Test
    fun singleDayRangeCoversExactlyThatDay() {
        val day = LocalDate.of(2026, 11, 5)
        val r = customChartRange(day, day, zone)
        assertEquals(millis(day), r.startMillis)
        assertEquals(millis(day.plusDays(1)), r.endMillis)
    }

    @Test
    fun dailyPointsOnlyForMonthAndShortCustomRanges() {
        val start = LocalDate.of(2026, 1, 1)
        val short = customChartRange(start, start.plusDays(61), zone) // 62 дня
        val long = customChartRange(start, start.plusDays(62), zone) // 63 дня
        assertTrue(usesDailyPoints(ChartPeriod.CUSTOM, short))
        assertFalse(usesDailyPoints(ChartPeriod.CUSTOM, long))
        assertTrue(usesDailyPoints(ChartPeriod.MONTH, long))
        assertFalse(usesDailyPoints(ChartPeriod.YEAR, short))
        assertFalse(usesDailyPoints(ChartPeriod.ALL, short))
    }
}
