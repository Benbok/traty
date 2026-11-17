package ru.my.traty.ui.apartments

import ru.my.traty.data.apartments.ReadingDates
import ru.my.traty.ui.formatDateNumeric
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Пользовательские даты; формат хранения и расчётов не меняется. */
internal object HousingDates {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("ru"))
        .withResolverStyle(ResolverStyle.STRICT)
    private val monthFormat = DateTimeFormatter.ofPattern("MM.uuuu", Locale.forLanguageTag("ru"))
        .withResolverStyle(ResolverStyle.STRICT)

    fun format(date: LocalDate): String = formatDateNumeric(date)
    fun format(value: Long): String = format(ReadingDates.decode(value))
    fun parse(text: String): LocalDate = LocalDate.parse(text.trim(), dateFormat)
    fun formatMonth(month: YearMonth): String = month.format(monthFormat)
    fun formatMonth(value: Int): String = formatMonth(YearMonth.of(value / 100, value % 100))
    fun parseMonth(text: String): YearMonth = YearMonth.parse(text.trim(), monthFormat)
}
