package ru.my.traty.data.apartments

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ConsumptionTest {
    private fun point(day: String, value: Long) = ReadingPoint(ReadingDates.encode(LocalDate.parse(day)), value)

    @Test fun fixedPointInputRejectsRoundingAndOverflow() {
        assertEquals(12345L, ReadingValues.parseMilli("12,345"))
        assertEquals(0L, ReadingValues.parseMilli("0"))
        listOf("12.3456", "-1", "NaN", "9223372036854775808").forEach {
            assertNull(it, ReadingValues.parseMilli(it))
        }
    }

    @Test fun missingAugustIsOneRealIntervalAndLateReadingSplitsIt() {
        val a = point("2026-07-25", 100000)
        val b = point("2026-09-10", 112000)
        val interval = Consumption.intervals(listOf(a, b)).single()
        assertEquals(12000L, interval.milli)
        assertEquals(47L, interval.days)
        assertNull(Consumption.compareLastTwo(listOf(a, b)))
        val split = Consumption.intervals(listOf(b, point("2026-08-25", 108000), a))
        assertEquals(listOf(8000L, 4000L), split.map { it.milli })
        assertEquals(listOf(31L, 16L), split.map { it.days })
    }

    @Test fun firstReadingIsNotConsumptionAndZeroBaseHasNoPercent() {
        val a = point("2026-01-01", 1000)
        assertTrue(Consumption.intervals(listOf(a)).isEmpty())
        val c = Consumption.compareLastTwo(listOf(a, point("2026-02-01", 1000), point("2026-03-01", 3000)))
        assertNotNull(c)
        assertNull(c?.dailyChangePercent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decreasingHistoryIsRejected() {
        Consumption.intervals(listOf(point("2026-01-01", 2000), point("2026-02-01", 1000)))
    }

    @Test fun groupRequiresExactSharedBoundaries() {
        val a = RegisterSeries(1, 1, "TOTAL", listOf(point("2026-01-01", 0), point("2026-02-01", 1000)), ReadingDates.encode(LocalDate.parse("2026-01-01")))
        val b = RegisterSeries(2, 2, "TOTAL", listOf(point("2026-01-02", 0), point("2026-02-01", 2000)), ReadingDates.encode(LocalDate.parse("2026-01-01")))
        assertNull(ConsumptionGroups.forRange(listOf(a, b), a.points.first().measuredAt, a.points.last().measuredAt))
        val aligned = b.copy(points = listOf(point("2026-01-01", 0), point("2026-01-15", 500), point("2026-02-01", 2000)))
        assertEquals(3000L, ConsumptionGroups.forRange(listOf(a, aligned), a.points.first().measuredAt, a.points.last().measuredAt)?.milli)
    }

    @Test fun archivedMeterCoverageIsNotInvented() {
        val points = listOf(point("2026-01-01", 0), point("2026-02-01", 1000))
        val meter = RegisterSeries(1, 1, "TOTAL", points, null, null, true)
        assertNull(ConsumptionGroups.forRange(listOf(meter), points[0].measuredAt, points[1].measuredAt))
    }

    @Test fun selectedDateSurvivesTimezoneChanges() {
        val day = LocalDate.parse("2026-03-29")
        assertEquals(day, ReadingDates.decode(ReadingDates.encode(day)))
    }
}
