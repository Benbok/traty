package ru.my.traty.ui.charts.model

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.Income
import java.time.LocalDate
import java.time.ZoneOffset

class CashFlowIntervalsTest {
    @Test
    fun weekBoundsAreClippedAndDrillDownPreservesDailyAmounts() {
        val start = LocalDate.of(2026, 2, 4)
        val end = start.plusDays(62)
        val granularity = cashFlowGranularity(start, end)
        val weeks = cashFlowIntervals(start, end, 1, granularity)
        val firstWeek = start..start.plusDays(4)
        assertEquals(CashFlowGranularity.WEEK, granularity)
        assertEquals(firstWeek, weeks.first())
        assertEquals(end, weeks.last().endInclusive)

        val zone = ZoneOffset.UTC
        val graph = buildCashFlowSelection(
            intervals = weeks,
            granularity = granularity,
            expenses = emptyList(),
            incomes = listOf(
                Income(amountKopecks = 100L, source = "Оклад", timestamp = start.atStartOfDay(zone).toInstant().toEpochMilli()),
                Income(amountKopecks = 150L, source = "Оклад", timestamp = firstWeek.endInclusive.atStartOfDay(zone).toInstant().toEpochMilli()),
                Income(amountKopecks = 200L, source = "Оклад", timestamp = end.atStartOfDay(zone).toInstant().toEpochMilli()),
            ),
            kind = CashFlowKind.INCOME,
            requested = emptyList(),
            zone = zone,
        ).graph

        val weeklyAmounts = graph.series.single().amounts
        assertEquals(250L, weeklyAmounts.first())
        assertEquals(0L, weeklyAmounts[1])
        assertEquals(200L, weeklyAmounts.last())

        val drilled = graph.daysWithin(firstWeek)
        assertEquals(CashFlowGranularity.DAY, drilled.granularity)
        assertEquals(listOf(100L, 0L, 0L, 0L, 150L), drilled.series.single().amounts)
    }

    @Test
    fun monthlyBucketsFollowBillingDayWithinTheRequestedRange() {
        val start = LocalDate.of(2026, 7, 16)
        val end = LocalDate.of(2026, 11, 21)
        val granularity = cashFlowGranularity(start, end)
        val months = cashFlowIntervals(start, end, 10, granularity)

        assertEquals(CashFlowGranularity.MONTH, granularity)
        assertEquals(start..LocalDate.of(2026, 8, 9), months.first())
        assertEquals(LocalDate.of(2026, 11, 10)..end, months.last())
    }
}
