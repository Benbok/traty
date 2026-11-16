package ru.my.traty.capture

import ru.my.traty.ai.ParsedEntry
import java.time.LocalDate

/**
 * Дата от ИИ на здравый смысл. Трата не бывает в будущем; а дата старше двух месяцев без слов о дате во фразе
 * («5 октября», «две недели назад») почти всегда ошибка модели, например прошлый год. Такая дата заменяется
 * сегодняшним днём (дата не названа), а человек видит пояснение в окне проверки.
 */
object DateCheck {
    /** Дольше этого срока назад дата без слов о дате во фразе считается ошибкой. */
    const val MAX_AGE_DAYS = 60L

    class Result(val entries: List<ParsedEntry>, val problems: List<String>)

    private val monthOrCount = Regex(
        """(?<![\p{L}])(?:январ\p{L}*|феврал\p{L}*|март\p{L}*|апрел\p{L}*|ма[яе]|июн\p{L}*|июл\p{L}*|август\p{L}*|сентябр\p{L}*|октябр\p{L}*|ноябр\p{L}*|декабр\p{L}*|числ\p{L}*|назад|прошл\p{L}*|недел\p{L}*|месяц\p{L}*|год\p{L}*|вчера|позавчера)(?![\p{L}])|\d{1,2}[./]\d{1,2}""",
        RegexOption.IGNORE_CASE,
    )

    fun apply(entries: List<ParsedEntry>, rawText: String, today: LocalDate): Result {
        val dateNamed = monthOrCount.containsMatchIn(rawText)
        val problems = mutableListOf<String>()
        val fixed = entries.map { e ->
            val raw = e.date?.trim()?.takeIf { it.isNotEmpty() } ?: return@map e.copy(date = null)
            val day = runCatching { LocalDate.parse(raw) }.getOrNull()
            val why = when {
                day == null -> "ИИ вернул непонятную дату «${raw.take(12)}»"
                day.isAfter(today) -> "ИИ поставил дату ${show(day)}, она ещё не наступила"
                !dateNamed && day.isBefore(today.minusDays(MAX_AGE_DAYS)) ->
                    "ИИ поставил дату ${show(day)}, а во фразе её нет"
                else -> null
            }
            if (why == null) e else {
                problems += "$why, поставлена сегодняшняя."
                e.copy(date = null)
            }
        }
        return Result(fixed, problems.distinct())
    }

    private fun show(d: LocalDate) = "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
}
