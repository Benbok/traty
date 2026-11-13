package ru.my.traty.ui.charts

import ru.my.traty.ui.charts.model.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** До скольких дней (включительно) произвольный период рисуется точками по дням, дальше — по месяцам. */
const val DAILY_POINTS_MAX_DAYS = 62

/** Произвольный период «с даты по дату», обе даты включительно; перепутанные даты меняются местами. */
fun customChartRange(a: LocalDate, b: LocalDate, zone: ZoneId): ChartRange {
    val start = if (a.isAfter(b)) b else a
    val end = if (a.isAfter(b)) a else b
    return ChartRange(
        startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        startDate = start,
        endDate = end,
    )
}

/** Точки графиков по дням — для «Месяц» и для коротких произвольных периодов; иначе по месяцам. */
fun usesDailyPoints(period: ChartPeriod, range: ChartRange): Boolean = when (period) {
    ChartPeriod.MONTH -> true
    ChartPeriod.CUSTOM -> ChronoUnit.DAYS.between(range.startDate, range.endDate) < DAILY_POINTS_MAX_DAYS
    else -> false
}
