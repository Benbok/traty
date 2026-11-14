package ru.my.traty.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CumulativeSpendingTest {
    private val start = LocalDate.of(2026, 11, 10)
    private val end = LocalDate.of(2026, 12, 9)
    private fun d(day: Int) = start.plusDays(day.toLong() - 1)

    @Test
    fun runningPeriodStopsAtToday() {
        val l = CumulativeSpending.line(start, end, d(4), listOf(d(1) to 100L, d(1) to 50L, d(3) to 200L, d(10) to 999L))
        assertEquals(30, l.daysTotal)
        // День 2 без трат — линия ровная; трата 10-го дня ещё не видна
        assertEquals(listOf(150L, 150L, 350L, 350L), l.points)
        assertEquals(350L, l.last)
    }

    @Test
    fun finishedPeriodHasAllDaysAndIgnoresOutside() {
        val l = CumulativeSpending.line(start, end, end.plusDays(5), listOf(start.minusDays(1) to 7L, d(30) to 10L))
        assertEquals(30, l.points.size)
        assertEquals(0L, l.points[28])
        assertEquals(10L, l.last)
    }

    @Test
    fun futurePeriodIsEmpty() {
        assertEquals(emptyList<Long>(), CumulativeSpending.line(start, end, start.minusDays(1), emptyList()).points)
    }

    @Test
    fun vsPreviousComparesSameDay() {
        val cur = CumulativeLine(start, 30, listOf(100L, 300L, 500L))
        val prev = CumulativeLine(start.minusMonths(1), 31, (1..31).map { it * 100L })
        val p = CumulativeSpending.vsPrevious(cur, prev)!!
        assertEquals(3, p.day)
        assertEquals(200L, p.diff) // 500 против 300
    }

    @Test
    fun shorterPreviousUsesItsLastDay() {
        val cur = CumulativeLine(start, 31, (1..31).map { 10L })
        val prev = CumulativeLine(start, 28, (1..28).map { it.toLong() })
        assertEquals(28L, CumulativeSpending.vsPrevious(cur, prev)!!.previous)
    }

    @Test
    fun nothingToCompare() {
        assertNull(CumulativeSpending.vsPrevious(CumulativeLine(start, 30, emptyList()), CumulativeLine(start, 30, listOf(1L))))
    }
}
