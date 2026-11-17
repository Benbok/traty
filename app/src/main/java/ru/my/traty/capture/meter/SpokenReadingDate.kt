package ru.my.traty.capture.meter

import java.time.LocalDate

internal data class ReadingDateText(val text: String, val date: LocalDate?)

internal object SpokenReadingDate {
    private val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val ordinals = listOf("первое", "второе", "третье", "четвертое", "пятое", "шестое",
        "седьмое", "восьмое", "девятое", "десятое", "одиннадцатое", "двенадцатое",
        "тринадцатое", "четырнадцатое", "пятнадцатое", "шестнадцатое", "семнадцатое",
        "восемнадцатое", "девятнадцатое", "двадцатое", "двадцать первое", "двадцать второе",
        "двадцать третье", "двадцать четвертое", "двадцать пятое", "двадцать шестое",
        "двадцать седьмое", "двадцать восьмое", "двадцать девятое", "тридцатое", "тридцать первое")
    private val days = ordinals.mapIndexed { i, word -> word to i + 1 }.toMap()
    private val prefix = """(?:(?:за|от|на|дата)\s+)?"""
    private val relative = Regex("""(?<!\p{L})""" + prefix + """(позавчера|вчера|сегодня)(?!\p{L})""")
    private val numeric = Regex("""(?<![\d.])""" + prefix + """(\d{1,2})[./](\d{1,2})[./](\d{4})(?!\d)""")
    private val named = Regex(
        """(?<![\p{L}\d])""" + prefix + """(\d{1,2}|""" +
            ordinals.sortedByDescending { it.length }.joinToString("|") + """)\s+(""" +
            months.joinToString("|") + """)(?:\s+(\d{4})(?:\s+года)?)?(?![\p{L}\d])""",
    )

    fun extract(text: String, today: LocalDate): ReadingDateText {
        val found = mutableListOf<Pair<IntRange, LocalDate>>()
        relative.findAll(text).forEach { m ->
            val back = when (m.groupValues[1]) { "вчера" -> 1L; "позавчера" -> 2L; else -> 0L }
            found += m.range to today.minusDays(back)
        }
        try {
            numeric.findAll(text).forEach { m ->
                found += m.range to LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }
            named.findAll(text).forEach { m ->
                val day = m.groupValues[1].toIntOrNull() ?: days.getValue(m.groupValues[1])
                val year = m.groupValues[3].toIntOrNull() ?: today.year
                found += m.range to LocalDate.of(year, months.indexOf(m.groupValues[2]) + 1, day)
            }
        } catch (_: java.time.DateTimeException) {
            throw IllegalArgumentException("Проверьте названную дату")
        }
        require(found.size <= 1) { "Назовите одну дату снятия показаний" }
        val match = found.singleOrNull() ?: return ReadingDateText(text, null)
        require(!match.second.isAfter(today)) { "Дата снятия показаний не может быть в будущем" }
        return ReadingDateText(text.replaceRange(match.first, " "), match.second)
    }
}
