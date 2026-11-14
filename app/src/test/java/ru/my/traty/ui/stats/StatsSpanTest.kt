package ru.my.traty.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsSpanTest {
    private val sat = LocalDate.of(2026, 11, 14) // суббота

    @Test
    fun weekIsMondayToSunday() {
        assertEquals(LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 15), weekRange(sat))
        assertEquals(weekRange(sat), weekRange(LocalDate.of(2026, 11, 9)))
        assertEquals(weekRange(sat), weekRange(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun shiftingWeekGivesNeighbourWeek() {
        val (a, b) = weekRange(sat)
        assertEquals(weekRange(sat.plusDays(7)), shiftRange(a, b, 1))
        assertEquals(StatsSpan.WEEK, spanOf(shiftRange(a, b, -1)))
    }

    @Test
    fun spanDetection() {
        assertEquals(StatsSpan.PERIOD, spanOf(null))
        assertEquals(StatsSpan.DAY, spanOf(dayRange(sat)))
        assertEquals(StatsSpan.WEEK, spanOf(weekRange(sat)))
        // 7 дней, но не с понедельника — это «Даты»
        assertEquals(StatsSpan.DATES, spanOf(sat to sat.plusDays(6)))
        assertEquals(StatsSpan.DATES, spanOf(sat.minusDays(29) to sat))
    }
}
