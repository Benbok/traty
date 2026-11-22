package ru.my.traty.ui.charts.model

import java.time.LocalDate

/** Накопленные суммы с начала выбранного диапазона на конец [date]. */
data class CashFlowPoint(
    val date: LocalDate,
    val incomeKopecks: Long,
    val expenseKopecks: Long,
) {
    val netKopecks: Long get() = incomeKopecks - expenseKopecks
}

/** Интервалы идут по возрастанию, не пересекаются и обрезаны границами выбранного диапазона. */
fun cumulativeCashFlowPoints(
    intervals: List<ClosedRange<LocalDate>>,
    incomeByDate: Map<LocalDate, Long>,
    expenseByDate: Map<LocalDate, Long>,
): List<CashFlowPoint> {
    val income = cumulativeCashFlowAmounts(intervals, incomeByDate)
    val expense = cumulativeCashFlowAmounts(intervals, expenseByDate)
    return intervals.mapIndexed { index, interval ->
        CashFlowPoint(interval.endInclusive, income[index], expense[index])
    }
}

internal fun cumulativeCashFlowAmounts(
    intervals: List<ClosedRange<LocalDate>>,
    amounts: Map<LocalDate, Long>,
): List<Long> {
    var total = 0L
    return intervals.map { interval ->
        total += amounts.filterKeys { it in interval }.values.sum()
        total
    }
}
