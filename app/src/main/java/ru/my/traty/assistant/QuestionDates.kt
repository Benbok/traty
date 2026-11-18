package ru.my.traty.assistant

import ru.my.traty.data.period.BillingPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Отрезок дат, названный в вопросе («в октябре», «летом», «вчера»), включительно. */
data class DateSpan(val from: LocalDate, val to: LocalDate)

/**
 * Даты из вопроса распознаёт приложение, а не ИИ: модели путают год («в декабре» в ноябре — это прошлый декабрь),
 * времена года и прошлый период. Если в вопросе есть понятная дата, она заменяет даты из ответа ИИ.
 */
object QuestionDates {
    /** Для этих вопросов важен срок; у плана, подписок и «могу ли я» свои правила. */
    private val DATED = setOf(
        Intents.TOTAL, Intents.TOP, Intents.BREAKDOWN, Intents.PLACES, Intents.COMPARE,
        Intents.AVERAGE, Intents.INCOME, Intents.TREND, Intents.LIST,
        Intents.DELETE_EXPENSE, Intents.MOVE_EXPENSE,
    )

    /** «за всё время»: дальше движок заменит эту дату первой записью. */
    val ALL_TIME: LocalDate = LocalDate.of(2000, 1, 1)
    internal const val ALL_TIME_WORDS = """за\s+(?:вс[её]\s+время|всю\s+историю|весь\s+период)"""

    fun apply(question: String, q: AssistantQuery, today: LocalDate, startDay: Int): AssistantQuery {
        if (q.intent !in DATED) return q
        // В сравнении «сентябрь и октябрь» — два отрезка, в остальных вопросах — один общий
        val spans = find(question, today, startDay, joinMonths = q.intent != Intents.COMPARE)
        val fixed = fixFuture(q, today)
        if (spans.isEmpty()) return fixed
        if (q.intent == Intents.COMPARE) {
            if (spans.size < 2) return fixed
            // Сравниваем более позднее с более ранним: «лето и осень» — осень против лета
            val (earlier, later) = spans.take(2).sortedBy { it.from }.let { it[0] to it[1] }
            return fixed.copy(
                from = later.from.toString(), to = later.to.toString(),
                compareFrom = earlier.from.toString(), compareTo = earlier.to.toString(),
            )
        }
        // «в октябре и ноябре», «с октября по ноябрь» — общий отрезок
        return fixed.copy(from = spans.minOf { it.from }.toString(), to = spans.maxOf { it.to }.toString())
    }

    /** ИИ иногда ставит текущий год месяцу, который ещё не наступил: такой отрезок сдвигаем на год назад. */
    internal fun fixFuture(q: AssistantQuery, today: LocalDate): AssistantQuery {
        fun back(from: String?, to: String?): Pair<String?, String?> {
            val f = from?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() } ?: return from to to
            if (!f.isAfter(today)) return from to to
            val t = to?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            return f.minusYears(1).toString() to (t?.minusYears(1)?.toString() ?: to)
        }
        val (from, to) = back(q.from, q.to)
        val (cFrom, cTo) = back(q.compareFrom, q.compareTo)
        return q.copy(from = from, to = to, compareFrom = cFrom, compareTo = cTo)
    }

    /** Все отрезки в порядке упоминания. Совпадения не пересекаются: «с 1 по 10 октября» — один отрезок. */
    fun find(question: String, today: LocalDate, startDay: Int, joinMonths: Boolean = true): List<DateSpan> {
        val text = question.lowercase().replace('ё', 'е')
        val hits = ArrayList<Pair<IntRange, DateSpan>>()
        for (rule in rules(today, startDay, joinMonths)) {
            rule.regex.findAll(text).forEach { m ->
                if (hits.none { it.first.first <= m.range.last && m.range.first <= it.first.last }) {
                    rule.span(m)?.let { s -> hits += m.range to s.clampTo(today) }
                }
            }
        }
        return hits.sortedBy { it.first.first }.map { it.second }
    }

    private class Rule(pattern: String, val span: (MatchResult) -> DateSpan?) {
        val regex = Regex("(?<![\\p{L}\\d])(?:$pattern)(?![\\p{L}])")
    }

    private fun DateSpan.clampTo(today: LocalDate) = if (to.isAfter(today)) DateSpan(from, maxOf(from, today)) else this

    /** Месяц только в своих падежах: «мартини», «майонез», «августовский» месяцами не считаются. */
    private const val MONTH = "(январ(?:ь|я|ю|е|ём|ем)|феврал(?:ь|я|ю|е|ём|ем)|март(?:а|у|е|ом)?|апрел(?:ь|я|ю|е|ем)|ма[йяе]|июн(?:ь|я|ю|е|ем)|июл(?:ь|я|ю|е|ем)|август(?:а|у|е|ом)?|сентябр(?:ь|я|ю|е|ём|ем)|октябр(?:ь|я|ю|е|ём|ем)|ноябр(?:ь|я|ю|е|ём|ем)|декабр(?:ь|я|ю|е|ём|ем))"
    private val MONTH_STEMS = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")

    private fun monthOf(stem: String): Int = MONTH_STEMS.indexOfFirst { stem.startsWith(it) } + 1

    /** Месяц без года — последний такой, который уже наступил. */
    private fun yearFor(month: Int, yearText: String?, lastYear: Boolean, today: LocalDate): Int = when {
        !yearText.isNullOrEmpty() -> yearText.toInt()
        lastYear -> today.year - 1
        month > today.monthValue -> today.year - 1
        else -> today.year
    }

    private fun day(month: Int, d: String, yearText: String?, today: LocalDate): LocalDate? {
        val year = yearFor(month, yearText, false, today)
        return runCatching { LocalDate.of(year, month, d.toInt()) }.getOrNull()
    }

    private fun count(word: String): Int? = word.toIntOrNull() ?: when {
        word.startsWith("дв") || word.startsWith("пар") -> 2
        word.startsWith("тр") -> 3
        word.startsWith("четыр") -> 4
        word.startsWith("пят") -> 5
        word.startsWith("шест") -> 6
        word.isEmpty() -> 1
        else -> null
    }

    private fun rules(today: LocalDate, startDay: Int, joinMonths: Boolean): List<Rule> {
        val period = BillingPeriod.containing(today, startDay)
        val monday = today.with(DayOfWeek.MONDAY)
        val year = "(?:\\s+(\\d{4})(?:\\s*г\\S*)?)?"
        return listOf(
            // «с 1 по 10 октября», «с 5 октября по 3 ноября 2025»
            Rule("с\\s+(\\d{1,2})(?:\\s+$MONTH)?\\s+(?:по|до)\\s+(\\d{1,2})\\s+$MONTH$year") { m ->
                val g = m.groupValues
                val toMonth = monthOf(g[4])
                val to = day(toMonth, g[3], g[5], today) ?: return@Rule null
                val fromMonth = if (g[2].isNotEmpty()) monthOf(g[2]) else toMonth
                val from = runCatching { LocalDate.of(to.year, fromMonth, g[1].toInt()) }.getOrNull() ?: return@Rule null
                DateSpan(if (from.isAfter(to)) from.minusYears(1) else from, to)
            },
            // «с ноября по декабрь», «с октября 2025 по март 2026», «с ноября по декабрь прошлого года»
            Rule("(?:с|от)\\s+(?:начала\\s+)?$MONTH$year\\s+(?:по|до)\\s+(?:конец\\s+|конца\\s+)?$MONTH$year(\\s+прошлого\\s+года)?") { m ->
                monthRange(m.groupValues, today)
            },
            // «в ноябре и декабре» — один отрезок, а не два месяца из разных лет
            Rule("(?:в|за)\\s+$MONTH$year\\s+и\\s+$MONTH$year(\\s+прошлого\\s+года)?") { m ->
                if (joinMonths) monthRange(m.groupValues, today) else null
            },
            // «с ноября», «с начала октября» — с первого числа по сегодня
            Rule("с\\s+(?:начала\\s+)?$MONTH$year(\\s+прошлого\\s+года)?") { m ->
                val month = monthOf(m.groupValues[1])
                val y = yearFor(month, m.groupValues[2], m.groupValues[3].isNotEmpty(), today)
                DateSpan(LocalDate.of(y, month, 1), today)
            },
            // «5 октября»
            Rule("(\\d{1,2})\\s+$MONTH$year") { m ->
                day(monthOf(m.groupValues[2]), m.groupValues[1], m.groupValues[3], today)?.let { DateSpan(it, it) }
            },
            Rule("позавчера") { DateSpan(today.minusDays(2), today.minusDays(2)) },
            Rule("вчера") { DateSpan(today.minusDays(1), today.minusDays(1)) },
            Rule("сегодня") { DateSpan(today, today) },
            Rule("(?:на|за)\\s+(?:этой|эту|текущей|текущую)\\s+недел\\S*|с\\s+начала\\s+недели") { DateSpan(monday, today) },
            Rule("(?:на|за)\\s+(?:прошлой|прошлую|прошедшей|прошедшую)\\s+недел\\S*") {
                DateSpan(monday.minusWeeks(1), monday.minusDays(1))
            },
            Rule("(?:в|за|на)\\s+(?:этом|этот|текущем|текущий)\\s+(?:месяц\\S*|период\\S*)|с\\s+начала\\s+(?:месяца|периода)") {
                DateSpan(period.startDate, today)
            },
            Rule("(?:в|за)\\s+(?:позапрошлом|позапрошлый)\\s+(?:месяц\\S*|период\\S*)") {
                period.previous(startDay).previous(startDay).let { DateSpan(it.startDate, it.endDate) }
            },
            Rule("(?:в|за)\\s+(?:прошлом|прошлый|прошедшем|прошедший|предыдущем|предыдущий)\\s+(?:месяц\\S*|период\\S*)") {
                period.previous(startDay).let { DateSpan(it.startDate, it.endDate) }
            },
            // Кварталы календарные: январь–март, апрель–июнь, июль–сентябрь, октябрь–декабрь
            Rule("(?:в|за|на)\\s+(?:этом|этот|текущем|текущий)\\s+квартал\\S*|с\\s+начала\\s+квартала") {
                DateSpan(quarterStart(today), today)
            },
            Rule("(?:в|за)\\s+(?:прошлом|прошлый|прошедшем|прошедший|предыдущем|предыдущий)\\s+квартал\\S*") {
                val start = quarterStart(today).minusMonths(3)
                DateSpan(start, start.plusMonths(3).minusDays(1))
            },
            // «за первый квартал», «во 2 квартале 2025»; «за 2 квартала» — это полгода, ниже
            Rule("(?:в|во|за)\\s+(1|2|3|4|перв\\S*|втор\\S*|трет\\S*|четверт\\S*)\\s+квартал(?:е)?$year") { m ->
                val n = m.groupValues[1].let { w -> w.toIntOrNull() ?: when { w.startsWith("перв") -> 1; w.startsWith("втор") -> 2; w.startsWith("трет") -> 3; else -> 4 } }
                var start = LocalDate.of(m.groupValues[2].toIntOrNull() ?: today.year, (n - 1) * 3 + 1, 1)
                if (m.groupValues[2].isEmpty() && start.isAfter(today)) start = start.minusYears(1)
                DateSpan(start, start.plusMonths(3).minusDays(1))
            },
            // «за неделю», «за последние 3 месяца», «за полгода», «за пару недель»
            Rule("за\\s+(?:последн\\S+\\s+)?(\\d{1,3}|два|две|три|четыре|пять|шесть|пару|)\\s*(дн\\S*|день|недел\\S*|месяц\\S*|квартал\\S*|год\\S*|лет)|за\\s+(?:последн\\S+\\s+)?(полгода)") { m ->
                val g = m.groupValues
                if (g[3].isNotEmpty()) return@Rule DateSpan(today.minusMonths(6).plusDays(1), today)
                val n = count(g[1]) ?: return@Rule null
                val unit = g[2]
                // «за месяц» без «последний» слишком часто значит «в этом месяце» — оставляем ИИ
                if (g[1].isEmpty() && !m.value.contains("последн") && (unit.startsWith("месяц") || unit.startsWith("год"))) return@Rule null
                val from = when {
                    unit.startsWith("д") -> today.minusDays(n.toLong())
                    unit.startsWith("н") -> today.minusWeeks(n.toLong())
                    unit.startsWith("м") -> today.minusMonths(n.toLong())
                    // «за последний квартал» — последние три месяца, как «за последний год» — последние 12
                    unit.startsWith("к") -> today.minusMonths(3L * n)
                    else -> today.minusYears(n.toLong())
                }
                DateSpan(from.plusDays(1), today)
            },
            Rule("(?:в|за)\\s+(?:этом|этот|текущем|текущий)\\s+год(?:у)?|с\\s+начала\\s+года") { DateSpan(today.withDayOfYear(1), today) },
            Rule("(?:в|за)\\s+(?:прошлом|прошлый|предыдущем|предыдущий)\\s+год(?:у)?") {
                val y = today.year - 1
                DateSpan(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
            },
            Rule("(?:в|за)\\s+(\\d{4})\\s*(?:году|год|г\\.?)") { m ->
                val y = m.groupValues[1].toInt()
                if (y in 2000..today.year) DateSpan(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)) else null
            },
            Rule(ALL_TIME_WORDS) { DateSpan(ALL_TIME, today) },
            // «в октябре», «октябрь 2025», «в марте прошлого года»
            Rule("$MONTH$year(\\s+прошлого\\s+года)?") { m ->
                val month = monthOf(m.groupValues[1])
                val y = yearFor(month, m.groupValues[2], m.groupValues[3].isNotEmpty(), today)
                val ym = YearMonth.of(y, month)
                DateSpan(ym.atDay(1), ym.atEndOfMonth())
            },
            // «летом», «прошлой осенью», «зимой 2025»; «зимнюю куртку» и «летние шины» — не время
            Rule("(прошл\\S*\\s+)?(зим(?:а|у|ы|е|ой|ою)|весн(?:а|у|ы|е|ой|ою)|лето|летом|лета|лету|осен(?:ь|и|ью))$year") { m ->
                season(m.groupValues[2], m.groupValues[1].isNotEmpty(), m.groupValues[3], today)
            },
        )
    }

    private fun quarterStart(d: LocalDate): LocalDate = LocalDate.of(d.year, (d.monthValue - 1) / 3 * 3 + 1, 1)

    /**
     * «с ноября по декабрь»: [g] — группы правила (месяц, год, месяц, год, «прошлого года»).
     * Без года отрезок — последний уже начавшийся: в ноябре «с ноября по декабрь» — с 1 ноября по сегодня,
     * «с декабря по февраль» — с прошлого декабря. Если второй месяц раньше первого, отрезок переходит через Новый год.
     */
    private fun monthRange(g: List<String>, today: LocalDate): DateSpan {
        val m1 = monthOf(g[1])
        val m2 = monthOf(g[3])
        val wrap = if (m2 < m1) 1 else 0
        val y1 = g[2].toIntOrNull()
        val y2 = g[4].toIntOrNull()
        val fromYear = when {
            y1 != null -> y1
            y2 != null -> y2 - wrap
            g[5].isNotEmpty() -> today.year - 1 - wrap
            else -> yearFor(m1, null, false, today)
        }
        val from = YearMonth.of(fromYear, m1).atDay(1)
        val to = YearMonth.of(y2 ?: (fromYear + wrap), m2).atEndOfMonth()
        return if (to.isBefore(from)) DateSpan(YearMonth.of(y2 ?: fromYear, m2).atDay(1), YearMonth.of(fromYear, m1).atEndOfMonth()) else DateSpan(from, to)
    }

    /** Время года: последнее уже начавшееся (зима — с декабря по февраль). */
    private fun season(word: String, previous: Boolean, yearText: String, today: LocalDate): DateSpan? {
        val startMonth = when {
            word.startsWith("зим") -> 12
            word.startsWith("весн") -> 3
            word.startsWith("лет") -> 6
            word.startsWith("осен") -> 9
            else -> return null
        }
        fun startIn(y: Int) = LocalDate.of(if (startMonth == 12) y - 1 else y, startMonth, 1)
        var start = if (yearText.isNotEmpty()) startIn(yearText.toInt()) else startIn(today.year).let { if (it.isAfter(today)) startIn(today.year - 1) else it }
        if (previous && yearText.isEmpty()) start = start.minusYears(1)
        return DateSpan(start, start.plusMonths(3).minusDays(1))
    }
}
