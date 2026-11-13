package ru.my.traty.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsRangesTest {
    private val from = LocalDate.of(2026, 10, 12)
    private val to = LocalDate.of(2026, 11, 9) // 29 дней, обе даты включительно

    @Test
    fun lengthCountsBothEndDates() {
        assertEquals(29L, rangeLengthDays(from, to))
        assertEquals(1L, rangeLengthDays(from, from))
    }

    @Test
    fun previousRangeIsAdjacentAndSameLength() {
        val (pFrom, pTo) = previousRange(from, to)
        assertEquals(LocalDate.of(2026, 10, 11), pTo) // день перед началом
        assertEquals(29L, rangeLengthDays(pFrom, pTo))
        assertEquals(LocalDate.of(2026, 9, 13), pFrom)
    }

    @Test
    fun previousOfSingleDayIsThePreviousDay() {
        val (pFrom, pTo) = previousRange(from, from)
        assertEquals(from.minusDays(1), pFrom)
        assertEquals(from.minusDays(1), pTo)
    }

    @Test
    fun shiftMovesWholeWindowWithoutGapOrOverlap() {
        val (nFrom, nTo) = shiftRange(from, to, 1)
        assertEquals(to.plusDays(1), nFrom)
        assertEquals(29L, rangeLengthDays(nFrom, nTo))

        val (bFrom, bTo) = shiftRange(from, to, -1)
        assertEquals(from.minusDays(1), bTo)
        assertEquals(previousRange(from, to), bFrom to bTo)
    }

    @Test
    fun shiftThereAndBackReturnsTheSameWindow() {
        val forward = shiftRange(from, to, 1)
        assertEquals(from to to, shiftRange(forward.first, forward.second, -1))
    }
}
