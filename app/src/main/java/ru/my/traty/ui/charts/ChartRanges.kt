package ru.my.traty.ui.charts

import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.RU
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
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

/** Сколько расчётных периодов охватывает фильтр («Месяц» = 1, «3 мес» = 3 …); null — не набор периодов. */
fun ChartPeriod.billingPeriodCount(): Int? = when (this) {
    ChartPeriod.MONTH -> 1
    ChartPeriod.QUARTER -> 3
    ChartPeriod.HALF_YEAR -> 6
    ChartPeriod.YEAR -> 12
    ChartPeriod.ALL, ChartPeriod.CUSTOM -> null
}

/**
 * Окно из [count] расчётных периодов, заканчивающееся периодом [last]:
 * «3 мес» при дне начала 10 и текущем периоде 10 ноя — 9 дек = 10 сен — 9 дек.
 */
fun billingPeriodsRange(last: BillingPeriod, count: Int, startDay: Int, zone: ZoneId): ChartRange {
    var first = last
    repeat((count - 1).coerceAtLeast(0)) { first = first.previous(startDay) }
    return ChartRange(
        startMillis = first.startMillis(zone),
        endMillis = last.endMillisExclusive(zone),
        startDate = first.startDate,
        endDate = last.endDate,
    )
}

/**
 * Расчётные периоды, на которые делится отрезок [start]..[end] для точек и столбиков «по месяцам».
 * Крайние периоды обрезаются по границам отрезка, чтобы суммы не выходили за выбранные даты.
 */
fun billingBuckets(start: LocalDate, end: LocalDate, startDay: Int): List<BillingPeriod> {
    if (end.isBefore(start)) return emptyList()
    val out = mutableListOf<BillingPeriod>()
    var p = BillingPeriod.containing(start, startDay)
    while (!p.startDate.isAfter(end)) {
        out += BillingPeriod(maxOf(p.startDate, start), minOf(p.endDate, end))
        p = p.next(startDay)
    }
    return out
}

/** Подпись столбика/точки: месяц, с которого начинается расчётный период («Окт»). */
fun bucketShortLabel(bucket: BillingPeriod): String =
    bucket.startDate.month.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }

/** Полная подпись: «Октябрь 2026» для целого календарного месяца, иначе точные даты «10 окт — 9 ноя». */
fun bucketFullLabel(bucket: BillingPeriod): String {
    val ym = YearMonth.from(bucket.startDate)
    return if (bucket.startDate == ym.atDay(1) && bucket.endDate == ym.atEndOfMonth()) {
        formatMonth(ym)
    } else {
        formatDateSpan(bucket.startDate, bucket.endDate)
    }
}
