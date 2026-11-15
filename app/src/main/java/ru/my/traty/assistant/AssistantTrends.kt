package ru.my.traty.assistant

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.ui.formatRub
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Как выглядит отрезок в «по месяцам / по неделям / по дням недели / по дням». */
object GroupBy {
    const val MONTH = "month"
    const val WEEK = "week"
    const val WEEKDAY = "weekday"
    const val DAY = "day"
}

/**
 * Вопросы о том, как менялись траты («по месяцам», «в какой день недели больше всего»)
 * и о самих покупках («когда я последний раз был в кафе», «как часто я беру такси»).
 */
internal object AssistantTrends {
    private val RU: Locale = Locale.forLanguageTag("ru")
    private const val MAX_DAYS = 31
    private const val LIST_SHOWN = 10

    /** Срок по умолчанию, если в вопросе его нет; null — текущий период. */
    fun defaultFrom(q: AssistantQuery, today: LocalDate): LocalDate? = when (q.intent) {
        Intents.LIST -> QuestionDates.ALL_TIME
        Intents.TREND -> when (q.groupBy) {
            GroupBy.WEEK -> today.with(DayOfWeek.MONDAY).minusWeeks(7)
            GroupBy.DAY -> null
            GroupBy.WEEKDAY -> today.minusMonths(3).plusDays(1)
            else -> YearMonth.from(today).minusMonths(5).atDay(1)
        }
        else -> null
    }

    private class Bucket(val label: String, val from: LocalDate, val to: LocalDate, val rows: List<ExpenseWithCategory>) {
        val sum get() = rows.sumOf { it.expense.amountKopecks }
    }

    fun trend(q: AssistantQuery, rows: List<ExpenseWithCategory>, from: LocalDate, to: LocalDate, ctx: AssistantContext, subject: String?, base: String): AssistantData {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val group: String = when (q.groupBy) {
            GroupBy.DAY -> if (days > MAX_DAYS) GroupBy.WEEK else GroupBy.DAY
            GroupBy.WEEK, GroupBy.WEEKDAY -> q.groupBy
            else -> GroupBy.MONTH
        }
        if (group == GroupBy.WEEKDAY) return weekdays(rows, from, to, ctx, subject, base)
        val byDay = rows.groupBy { day(it.expense.timestamp, ctx.zone) }
        val buckets = slices(group, from, to).map { (a, b, label) ->
            Bucket(label, a, b, byDay.filterKeys { !it.isBefore(a) && !it.isAfter(b) }.values.flatten())
        }
        val unit = when (group) { GroupBy.WEEK -> "неделям"; GroupBy.DAY -> "дням"; else -> "месяцам" }
        val top = buckets.maxByOrNull { it.sum }
        val facts = buildString {
            append(base)
            appendLine("Траты по $unit за ${span(from, to)}${subject?.let { " ($it)" }.orEmpty()}:")
            buckets.forEach { b ->
                val partial = if (b.to == ctx.today && group != GroupBy.DAY) " (пока до ${date(b.to)})" else ""
                appendLine("- ${b.label}$partial: ${formatRub(b.sum)}, покупок: ${b.rows.size}")
            }
            if (top != null && top.sum > 0) {
                appendLine("Больше всего: ${top.label}, ${formatRub(top.sum)}.")
                val full = buckets.filter { b ->
                    when (group) {
                        GroupBy.DAY -> true
                        GroupBy.WEEK -> ChronoUnit.DAYS.between(b.from, b.to) == 6L
                        else -> b.from.dayOfMonth == 1 && b.to == YearMonth.from(b.from).atEndOfMonth()
                    }
                }
                if (full.isNotEmpty() && buckets.size > 1) appendLine("В среднем за полный отрезок: ${formatRub(full.sumOf { it.sum } / full.size)}.")
            }
        }
        return AssistantData(facts, top?.let { formatRub(it.sum) }, top?.let { "больше всего — ${it.label}" }, ids(rows))
    }

    private fun slices(group: String, from: LocalDate, to: LocalDate): List<Triple<LocalDate, LocalDate, String>> {
        val out = ArrayList<Triple<LocalDate, LocalDate, String>>()
        var a = from
        while (!a.isAfter(to)) {
            val end = when (group) {
                GroupBy.DAY -> a
                GroupBy.WEEK -> a.with(DayOfWeek.SUNDAY)
                else -> YearMonth.from(a).atEndOfMonth()
            }.let { minOf(it, to) }
            val label = when (group) {
                GroupBy.DAY -> date(a)
                GroupBy.WEEK -> span(a, end)
                else -> YearMonth.from(a).month.getDisplayName(TextStyle.FULL_STANDALONE, RU) + " " + a.year
            }
            out += Triple(a, end, label)
            a = end.plusDays(1)
        }
        return out
    }

    /** По дням недели: сколько всего и в среднем за один такой день. */
    private fun weekdays(rows: List<ExpenseWithCategory>, from: LocalDate, to: LocalDate, ctx: AssistantContext, subject: String?, base: String): AssistantData {
        val dayCount = DayOfWeek.entries.associateWith { 0 }.toMutableMap()
        var d = from
        while (!d.isAfter(to)) { dayCount[d.dayOfWeek] = dayCount.getValue(d.dayOfWeek) + 1; d = d.plusDays(1) }
        val byWeekday = rows.groupBy { day(it.expense.timestamp, ctx.zone).dayOfWeek }
        val stats = DayOfWeek.entries.map { w ->
            val sum = byWeekday[w].orEmpty().sumOf { it.expense.amountKopecks }
            Triple(w.getDisplayName(TextStyle.FULL_STANDALONE, RU), sum, sum / dayCount.getValue(w).coerceAtLeast(1))
        }
        val top = stats.maxByOrNull { it.third }
        val facts = buildString {
            append(base)
            appendLine("Траты по дням недели за ${span(from, to)}${subject?.let { " ($it)" }.orEmpty()}:")
            stats.forEach { (name, sum, avg) -> appendLine("- $name: всего ${formatRub(sum)}, в среднем за день ${formatRub(avg)}") }
            if (top != null && top.second > 0) appendLine("Больше всего в среднем: ${top.first}.")
        }
        return AssistantData(facts, top?.let { formatRub(it.third) }, top?.let { "в среднем, ${it.first}" }, ids(rows))
    }

    /** «когда я последний раз…», «покажи траты в Пятёрочке», «как часто я езжу на такси». */
    fun list(rows: List<ExpenseWithCategory>, from: LocalDate, to: LocalDate, ctx: AssistantContext, subject: String?, base: String): AssistantData {
        val sorted = rows.sortedByDescending { it.expense.timestamp }
        val facts = buildString {
            append(base)
            appendLine("Траты за ${span(from, to)}${subject?.let { " ($it)" }.orEmpty()}: ${sorted.size} на ${formatRub(sorted.sumOf { it.expense.amountKopecks })}.")
            if (sorted.isEmpty()) {
                appendLine("Таких трат не найдено.")
                return@buildString
            }
            val last = day(sorted.first().expense.timestamp, ctx.zone)
            val ago = ChronoUnit.DAYS.between(last, ctx.today).toInt()
            appendLine("Последняя: ${date(last)}${if (ago > 0) ", $ago ${daysWord(ago)} назад" else ", сегодня"}.")
            val days = sorted.map { day(it.expense.timestamp, ctx.zone) }.distinct()
            if (days.size >= 2) {
                val every = ChronoUnit.DAYS.between(days.last(), days.first()).toDouble() / (days.size - 1)
                val rounded = Math.round(every * 10) / 10.0
                val word = if (rounded % 1.0 != 0.0) "дня" else daysWord(rounded.toInt())
                appendLine("В среднем раз в ${"%.1f".format(RU, rounded).removeSuffix(",0")} $word.")
            }
            appendLine("Последние траты:")
            sorted.take(LIST_SHOWN).forEach { appendLine("- ${describe(it, ctx.zone)}") }
        }
        val last = sorted.firstOrNull()?.let { day(it.expense.timestamp, ctx.zone) }
        return AssistantData(
            facts,
            last?.let { date(it) },
            last?.let { "последняя трата${subject?.let { s -> " · $s" }.orEmpty()}" },
            ids(sorted),
        )
    }
}
