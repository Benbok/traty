package ru.my.traty.data.apartments

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ConsumptionPeriodTest {
    @Test
    fun proratesOnlyThePartBetweenTheChosenDates() {
        val result = ConsumptionPeriod.calculate(listOf(series(1, 1, 11, 10_000)), day(3), day(8))
        assertEquals(ConsumptionPeriodResult(5_000, estimated = true, complete = true), result)
    }

    @Test
    fun combinesOldAndNewMeterAtReplacementBoundary() {
        val old = series(1, 1, 6, 5_000).copy(archived = true, accountingTo = day(6))
        val new = series(2, 6, 11, 10_000)
        assertEquals(
            ConsumptionPeriodResult(15_000, estimated = false, complete = true),
            ConsumptionPeriod.calculate(listOf(old, new), day(1), day(11)),
        )
    }

    @Test
    fun missingReadingsAndReplacementGapMakeResultPartial() {
        val old = series(1, 1, 5, 4_000).copy(archived = true, accountingTo = day(5))
        val new = series(2, 6, 10, 8_000)
        val missing = series(3, 1, 11, 0).copy(points = emptyList())
        val partial = ConsumptionPeriodResult(12_000, estimated = false, complete = false)

        assertEquals(partial, ConsumptionPeriod.calculate(listOf(old, new), day(1), day(10)))
        assertEquals(partial, ConsumptionPeriod.calculate(listOf(old, new, missing), day(1), day(11)))
        assertNull(ConsumptionPeriod.calculate(listOf(missing), day(1), day(11)))
    }

    @Test
    fun measuredZeroIsARealResult() {
        assertEquals(
            ConsumptionPeriodResult(0, estimated = false, complete = true),
            ConsumptionPeriod.calculate(listOf(series(1, 1, 11, 0)), day(1), day(11)),
        )
    }

    private fun series(id: Long, start: Int, end: Int, milli: Long) = RegisterSeries(
        id, id, "TOTAL", listOf(ReadingPoint(day(start), 0), ReadingPoint(day(end), milli)), day(start),
    )

    private fun day(day: Int) = ReadingDates.encode(LocalDate.of(2026, 10, day))
}
