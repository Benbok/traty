package ru.my.traty.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HeatCalendarTest {
    // 10 ноября 2026 — вторник
    private val start = LocalDate.of(2026, 11, 10)
    private val end = LocalDate.of(2026, 12, 9)

    @Test
    fun weekStartsOnMonday() {
        assertEquals(1, HeatCalendar.build(start, end, emptyList())!!.leadingBlanks)
        assertEquals(0, HeatCalendar.build(LocalDate.of(2026, 11, 9), end, emptyList())!!.leadingBlanks)
    }

    @Test
    fun levelsRelativeToMostExpensiveDay() {
        val m = HeatCalendar.build(
            start, end,
            listOf(start to 1000L, start.plusDays(1) to 100L, start.plusDays(2) to 500L, start.plusDays(2) to 10L, start.plusDays(3) to 250L),
        )!!
        assertEquals(30, m.days.size)
        assertEquals(listOf(4, 1, 3, 1, 0), m.days.take(5).map { it.level })
        assertEquals(510L, m.days[2].total)
        assertEquals(2, m.days[2].count)
        assertEquals(1000L, m.maxTotal)
    }

    @Test
    fun spendsOutsideAreIgnoredAndEmptyIsAllZero() {
        val m = HeatCalendar.build(start, end, listOf(start.minusDays(1) to 5L))!!
        assertEquals(0, m.days.sumOf { it.level })
    }

    @Test
    fun tooLongOrReversedRange() {
        assertNull(HeatCalendar.build(start, start.plusDays(62), emptyList()))
        assertNull(HeatCalendar.build(end, start, emptyList()))
    }
}
