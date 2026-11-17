package ru.my.traty.ui.charts.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashFlowPointTest {
    private val start = LocalDate.of(2026, 11, 10)

    @Test
    fun accumulatesIncomeAndExpensesIndependentlyIncludingQuietDays() {
        val days = (0L..3L).map { start.plusDays(it).let { day -> day..day } }
        val points = cumulativeCashFlowPoints(
            days,
            mapOf(start to 60_000_00L, start.plusDays(2) to 5_000_00L),
            mapOf(start.plusDays(1) to 10_000_00L, start.plusDays(3) to 5_000_00L),
        )
        assertEquals(listOf(60_000_00L, 60_000_00L, 65_000_00L, 65_000_00L), points.map { it.incomeKopecks })
        assertEquals(listOf(0L, 10_000_00L, 10_000_00L, 15_000_00L), points.map { it.expenseKopecks })
        assertEquals(50_000_00L, points.last().netKopecks)
    }

    @Test
    fun clipsMonthlyBucketsAndDoesNotResetTotalsBetweenThem() {
        val end = start.plusMonths(1).plusDays(3)
        val boundary = start.plusMonths(1)
        val points = cumulativeCashFlowPoints(
            listOf(start..boundary.minusDays(1), boundary..end),
            mapOf(start.minusDays(1) to 999L, start to 100L, boundary to 50L, end.plusDays(1) to 999L),
            mapOf(boundary.minusDays(1) to 30L, end to 40L),
        )
        assertEquals(CashFlowPoint(boundary.minusDays(1), 100L, 30L), points.first())
        assertEquals(CashFlowPoint(end, 150L, 70L), points.last())
    }

    @Test
    fun singleDayAndOnlyExpensesProduceNegativeDifference() {
        val points = cumulativeCashFlowPoints(listOf(start..start), emptyMap(), mapOf(start to 123_45L))
        assertEquals(listOf(CashFlowPoint(start, 0L, 123_45L)), points)
        assertEquals(-123_45L, points.single().netKopecks)
    }

    @Test
    fun preservesLongPrecisionAndIncomeOnly() {
        val amount = 9_007_199_254_740_993L
        val points = cumulativeCashFlowPoints(listOf(start..start), mapOf(start to amount), emptyMap())
        assertEquals(amount, points.single().incomeKopecks)
        assertEquals(amount, points.single().netKopecks)
    }

    @Test
    fun emptyIntervalsAndNoOperations() {
        assertTrue(cumulativeCashFlowPoints(emptyList(), emptyMap(), emptyMap()).isEmpty())
        assertEquals(
            listOf(CashFlowPoint(start, 0L, 0L)),
            cumulativeCashFlowPoints(listOf(start..start), emptyMap(), emptyMap()),
        )
    }
}
