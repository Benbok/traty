package ru.my.traty.ai

import java.time.DayOfWeek
import java.time.LocalDate

/** День из фразы и где он стоит в тексте. */
data class SpokenDay(val date: LocalDate, val range: IntRange)

/** «сегодня», «вчера», «позавчера», «в понедельник» — для разбора без ИИ. */
object SpokenDateParser {
    private val relative = Regex("""(?<![\p{L}])(позавчера|вчера|сегодня)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val weekday = Regex(
        """(?<![\p{L}])(?:в|во)\s+(понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )
    private val days = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "среду" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятницу" to DayOfWeek.FRIDAY, "субботу" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
    )

    fun find(text: String, today: LocalDate): SpokenDay? {
        relative.find(text)?.let { m ->
            val date = when (m.groupValues[1].lowercase()) {
                "вчера" -> today.minusDays(1)
                "позавчера" -> today.minusDays(2)
                else -> today
            }
            return SpokenDay(date, m.range)
        }
        weekday.find(text)?.let { m ->
            val target = days.getValue(m.groupValues[1].lowercase())
            // Ближайший прошедший такой день: трату записывают после покупки. Сегодняшний день недели — сегодня.
            val back = (today.dayOfWeek.value - target.value + 7) % 7
            return SpokenDay(today.minusDays(back.toLong()), m.range)
        }
        return null
    }
}
