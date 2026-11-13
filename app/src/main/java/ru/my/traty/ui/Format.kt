package ru.my.traty.ui

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

fun formatDate(d: LocalDate): String =
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, RU)} ${d.year}"

fun formatDateWithWeekday(d: LocalDate): String =
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, RU)}, ${d.dayOfWeek.getDisplayName(TextStyle.SHORT, RU)}"

private val rowFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(rowFmt)
