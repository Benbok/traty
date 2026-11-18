package ru.my.traty.data.apartments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConsumptionTrendTest {
    @Test
    fun resourceTrendUsesOnlySharedReadingsAndLeavesUnsupportedIntervalsOut() {
        val first = RegisterSeries(1, 1, "TOTAL", listOf(point(1, 100), point(3, 104), point(8, 109), point(10, 111)), date(1))
        val second = RegisterSeries(2, 2, "TOTAL", listOf(point(1, 50), point(8, 57), point(10, 59)), date(2))

        val trend = ConsumptionTrend.forResource(listOf(first, second), date(1), date(10))

        assertEquals(listOf(ConsumptionInterval(date(8), date(10), 4_000)), trend)
    }

    @Test
    fun resourceTrendKeepsTheIntervalEndingOnTheFirstDayOfThePeriod() {
        val readings = listOf(point(1, 100), point(3, 104))
        val series = listOf(RegisterSeries(1, 1, "TOTAL", readings, date(1)))

        assertEquals(
            listOf(ConsumptionInterval(date(1), date(3), 4_000)),
            ConsumptionTrend.forResource(series, date(3), date(8)),
        )
    }

    @Test
    fun includesBothBoundariesAndUsesReadingBeforeThePeriodAsBaseline() {
        val points = listOf(point(1, 100), point(3, 104), point(8, 109), point(9, 112))
        val trend = ConsumptionTrend.forPeriod(points, date(3), date(8))

        assertEquals(listOf(date(3), date(8)), trend.map { it.to })
        assertEquals(date(1), trend.first().from)
        assertEquals(2.0, trend.first().daily, 0.0)
        assertEquals(1.0, trend.last().daily, 0.0)
    }

    @Test
    fun doesNotInventConsumptionWithoutASecondReading() {
        assertTrue(ConsumptionTrend.forPeriod(listOf(point(3, 104)), date(1), date(8)).isEmpty())
    }

    private fun date(day: Int) = ReadingDates.encode(LocalDate.of(2026, 11, day))
    private fun point(day: Int, value: Long) = ReadingPoint(date(day), value * 1_000)
}
