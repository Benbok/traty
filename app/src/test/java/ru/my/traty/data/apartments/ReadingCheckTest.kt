package ru.my.traty.data.apartments

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadingCheckTest {
    private fun day(n: Long) = ReadingDates.encode(LocalDate.of(2026, 1, 1).plusDays(n))
    private fun point(n: Long, value: Long) = ReadingPoint(day(n), value)

    @Test fun firstReadingIsBaselineAndZeroConsumptionIsValid() {
        assertEquals(ReadingCheck(null, null), checkReading(day(0), 100L, emptyList()))
        val previous = point(0, 100L)
        assertEquals(ReadingCheck(previous, 0L), checkReading(day(30), 100L, listOf(previous)))
    }

    @Test fun preventsDuplicateDecreasingAndInvalidHistoricalInsertions() {
        val history = listOf(point(0, 100L), point(30, 200L))
        assertNotNull(checkReading(day(0), 100L, history).error)
        assertNotNull(checkReading(day(15), 99L, history).error)
        assertNotNull(checkReading(day(15), 201L, history).error)
        assertNotNull(checkReading(day(40), -1L, history).error)
        assertEquals(50L, checkReading(day(15), 150L, history).consumptionMilli)
    }

    @Test fun comparesDailyRatesAcrossDifferentIntervalLengths() {
        val history = listOf(point(0, 0L), point(30, 300L))
        // Раньше 10 в сутки; 300 за 10 дней — ровно втрое, 301 — уже больше.
        assertFalse(checkReading(day(40), 600L, history).unusuallyHigh)
        assertTrue(checkReading(day(40), 601L, history).unusuallyHigh)
        assertFalse(checkReading(day(90), 900L, history).unusuallyHigh)
    }

    @Test fun doesNotInventBaselineForHighConsumption() {
        assertFalse(checkReading(day(30), 999_000L, listOf(point(0, 100L))).unusuallyHigh)
        assertFalse(checkReading(day(60), 999_000L, listOf(point(0, 100L), point(30, 100L))).unusuallyHigh)
    }

    @Test fun handlesLargeLongValuesWithoutMultiplicationOverflow() {
        val history = listOf(point(0, 0L), point(1, Long.MAX_VALUE / 4))
        assertTrue(checkReading(day(2), Long.MAX_VALUE, history).unusuallyHigh)
        assertEquals(Long.MAX_VALUE, checkReading(day(1), Long.MAX_VALUE, listOf(point(0, 0L))).consumptionMilli)
    }
}
