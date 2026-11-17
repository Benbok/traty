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
    var income = 0L
    var expense = 0L
    return intervals.map { interval ->
        income += incomeByDate.filterKeys { it in interval }.values.sum()
        expense += expenseByDate.filterKeys { it in interval }.values.sum()
        CashFlowPoint(interval.endInclusive, income, expense)
    }
}
