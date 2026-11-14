package ru.my.traty.ui

import ru.my.traty.data.period.BillingPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

fun formatRub(kopecks: Long): String {
    val rub = kopecks / 100.0
    return if (kopecks % 100 == 0L) String.format(RU, "%,d ₽", kopecks / 100)
    else String.format(RU, "%,.2f ₽", rub)
}

fun formatRubSigned(kopecks: Long): String {
    val prefix = if (kopecks > 0) "+" else ""
    return prefix + formatRub(kopecks)
}

fun formatMonth(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } + " " + m.year

private val SHORT_MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

/**
 * Подпись расчётного периода: при дне начала 1 — «Ноябрь 2026», иначе «10 ноя — 9 дек»
 * (с годом, если период пересекает границу года: «10 дек — 9 янв 2027»).
 */
fun formatPeriod(p: BillingPeriod, startDay: Int): String {
    if (startDay == 1) return formatMonth(YearMonth.from(p.startDate))
    return formatDateSpan(p.startDate, p.endDate)
}

/** Короткий отрезок дат «10 окт — 9 ноя»; год добавляется, только если отрезок переходит через год. */
fun formatDateSpan(start: LocalDate, end: LocalDate): String {
    fun short(d: LocalDate) = "${d.dayOfMonth} ${SHORT_MONTHS[d.monthValue - 1]}"
    val year = if (start.year != end.year) " ${end.year}" else ""
    return "${short(start)} — ${short(end)}$year"
}

fun formatDate(d: LocalDate): String =
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, RU)} ${d.year}"

private val numericDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", RU)

/** Дата в формате «дд.мм.гггг», например «13.11.2026». */
fun formatDateNumeric(d: LocalDate): String = d.format(numericDateFmt)

fun formatDateWithWeekday(d: LocalDate): String =
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, RU)}, ${d.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)}"

private val rowFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(rowFmt)
