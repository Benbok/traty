package ru.my.traty.assistant

// Даты для ответов помощника: отрезок из запроса, отрезок для сравнения, подписи «1–14 ноября».

import ru.my.traty.data.PeriodComparison
import ru.my.traty.data.period.BillingPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru")
private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", RU)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)

/** Отрезок из запроса; без дат — текущий период до сегодня. Будущее обрезается сегодняшним днём. */
internal fun range(fromText: String?, toText: String?, defaultFrom: LocalDate, today: LocalDate): Pair<LocalDate, LocalDate> {
    var from = parse(fromText) ?: defaultFrom
    var to = parse(toText) ?: today
    if (to.isAfter(today)) to = today
    if (from.isAfter(to)) from = to.also { to = from }
    if (to.isAfter(today)) to = today
    return from to to
}

internal fun compareRange(q: AssistantQuery, from: LocalDate, to: LocalDate, period: BillingPeriod, ctx: AssistantContext): Pair<LocalDate, LocalDate> {
    parse(q.compareFrom)?.let { a ->
        val b = parse(q.compareTo) ?: a.plusDays(ChronoUnit.DAYS.between(from, to))
        return if (a.isAfter(b)) b to a else a to b
    }
    // Текущий период до сегодня сравниваем с тем же сроком прошлого, иначе — с отрезком той же длины перед ним
    if (from == period.startDate && to == ctx.today) {
        return PeriodComparison.comparableWindow(period, period.previous(ctx.startDay), ctx.today)
    }
    val len = ChronoUnit.DAYS.between(from, to) + 1
    return from.minusDays(len) to from.minusDays(1)
}

internal fun parse(s: String?): LocalDate? = s?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun day(ts: Long, zone: ZoneId) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

internal fun span(from: LocalDate, to: LocalDate): String = when {
    from == to -> date(from)
    from.year == to.year && from.month == to.month -> "${from.dayOfMonth}–${to.format(dayFmt)}"
    from.year == to.year -> "${from.format(dayFmt)} — ${to.format(dayFmt)}"
    else -> "${from.format(dayYearFmt)} — ${to.format(dayYearFmt)}"
}

internal fun date(d: LocalDate) = d.format(dayFmt)

internal fun header(ctx: AssistantContext) = "Сегодня ${ctx.today.format(dayYearFmt)}. Суммы в рублях.\n"
