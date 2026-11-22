package ru.my.traty.ui.charts.model

import ru.my.traty.ui.charts.billingBuckets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

/** Дни для месяца, недели для квартала, расчётные месяцы для более длинных периодов. */
internal fun cashFlowGranularity(start: LocalDate, end: LocalDate): CashFlowGranularity =
    when (ChronoUnit.DAYS.between(start, end) + 1) {
        in 1..62 -> CashFlowGranularity.DAY
        in 63..100 -> CashFlowGranularity.WEEK
        else -> CashFlowGranularity.MONTH
    }

internal fun cashFlowIntervals(
    start: LocalDate,
    end: LocalDate,
    startDay: Int,
    granularity: CashFlowGranularity,
): List<ClosedRange<LocalDate>> {
    if (end < start) return emptyList()
    return when (granularity) {
        CashFlowGranularity.DAY -> generateSequence(start) { it.plusDays(1) }
            .takeWhile { it <= end }.map { it..it }.toList()
        CashFlowGranularity.WEEK -> generateSequence(start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) {
            it.plusWeeks(1)
        }.takeWhile { it <= end }.map { maxOf(start, it)..minOf(end, it.plusDays(6)) }.toList()
        CashFlowGranularity.MONTH -> billingBuckets(start, end, startDay).map { it.startDate..it.endDate }
    }
}
