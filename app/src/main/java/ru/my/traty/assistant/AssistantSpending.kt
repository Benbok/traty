package ru.my.traty.assistant

// Вопросы «сколько», «самые крупные», «на что», «где» и «в среднем» по отобранным тратам.
// Вынесено из AssistantEngine: при вопросе про товар каждая трата засчитывается ценой самих товаров.

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Траты, попавшие в ответ, и сколько из каждой засчитывается ([Filter.itemPart]). */
internal class Picked(
    val rows: List<ExpenseWithCategory>,
    private val parts: Map<Long, ItemPart?>,
    private val zone: ZoneId,
) {
    constructor(rows: List<ExpenseWithCategory>, filter: Filter, ctx: AssistantContext) : this(
        rows, rows.associate { it.expense.id to filter.itemPart(it, ctx.expenseItems[it.expense.id].orEmpty()) }, ctx.zone,
    )

    fun amount(r: ExpenseWithCategory): Long = parts[r.expense.id]?.amount ?: r.expense.amountKopecks

    val sum: Long get() = rows.sumOf(::amount)

    /** Вопрос про товар: суммы — цены позиций, а не чеки целиком. */
    val byItem: Boolean get() = parts.values.any { it != null }

    fun notes(): List<String> = AssistantItems.notes(parts.values.filterNotNull())

    fun line(r: ExpenseWithCategory, withNote: Boolean = true): String =
        parts[r.expense.id]?.let { AssistantItems.line(r, it, zone) } ?: describe(r, zone, withNote)

    fun byCategory(): List<Pair<String, Long>> =
        rows.groupBy { it.categoryName }.map { (k, v) -> k to v.sumOf(::amount) }.sortedByDescending { it.second }
}

internal object AssistantSpending {
    private val RU: Locale = Locale.forLanguageTag("ru")

    private fun about(subject: String?) = subject?.let { " ($it)" }.orEmpty()

    fun total(p: Picked, unfiltered: Boolean, span: String, subject: String?, base: String): AssistantData {
        val sum = p.sum
        val count = if (p.byItem) "покупок с этим товаром" else "покупок"
        val facts = buildString {
            append(base)
            appendLine("Потрачено за $span${about(subject)}: ${formatRub(sum)}, $count: ${p.rows.size}.")
            p.notes().forEach(::appendLine)
            if (unfiltered && p.rows.isNotEmpty()) {
                appendLine("Больше всего:")
                p.byCategory().take(5).forEach { (name, s) -> appendLine("- $name: ${formatRub(s)} (${share(s, sum)})") }
            }
        }
        return AssistantData(facts, formatRub(sum), listOfNotNull(subject, span).joinToString(" · "), ids(p.rows))
    }

    fun top(p: Picked, limit: Int, span: String, subject: String?, base: String): AssistantData {
        val top = p.rows.sortedByDescending(p::amount).take(limit)
        val facts = buildString {
            append(base)
            appendLine("Самые крупные траты за $span${about(subject)}:")
            if (top.isEmpty()) appendLine("трат нет")
            // Заметки в ИИ не отправляем: для «самых крупных» хватает категории и места
            top.forEach { appendLine("- ${p.line(it, withNote = false)}") }
        }
        return AssistantData(facts, top.firstOrNull()?.let { formatRub(p.amount(it)) }, "Самая крупная · $span", top.map { it.expense.id })
    }

    fun breakdown(p: Picked, filter: Filter, span: String, subject: String?, base: String): AssistantData {
        val sum = p.sum
        // По одной категории разбиваем на подкатегории, иначе по категориям
        val bySub = filter.names.size == 1
        val groups = if (bySub) p.rows.groupBy { it.subcategoryName ?: "без подкатегории" }
            .map { (k, v) -> k to v.sumOf(p::amount) }.sortedByDescending { it.second }
        else p.byCategory()
        val facts = buildString {
            append(base)
            appendLine("Траты за $span${about(subject)} по ${if (bySub) "подкатегориям" else "категориям"}, всего ${formatRub(sum)}:")
            groups.take(10).forEach { (name, s) -> appendLine("- $name: ${formatRub(s)} (${share(s, sum)})") }
            p.notes().forEach(::appendLine)
        }
        return AssistantData(facts, formatRub(sum), listOfNotNull(subject, span).joinToString(" · "), ids(p.rows))
    }

    fun places(p: Picked, span: String, subject: String?, base: String): AssistantData {
        val withPlace = p.rows.filter { !it.expense.place.isNullOrBlank() }
        val groups = withPlace.groupBy { it.expense.place.orEmpty().trim().lowercase(RU) }
            .map { (_, v) -> v.first().expense.place.orEmpty().trim() to v }
            .sortedByDescending { (_, v) -> v.sumOf(p::amount) }
        val facts = buildString {
            append(base)
            appendLine("Места, где больше всего потрачено за $span${about(subject)}:")
            if (groups.isEmpty()) appendLine("у трат за этот срок не записаны места")
            groups.take(8).forEach { (name, v) ->
                appendLine("- $name: ${formatRub(v.sumOf(p::amount))}, покупок: ${v.size}")
            }
            val noPlace = p.rows.size - withPlace.size
            if (noPlace > 0) appendLine("Трат без места: $noPlace.")
            p.notes().forEach(::appendLine)
        }
        val top = groups.firstOrNull()
        return AssistantData(
            facts,
            top?.let { (_, v) -> formatRub(v.sumOf(p::amount)) },
            top?.let { "${it.first} · $span" },
            top?.second?.let { ids(it) }.orEmpty(),
        )
    }

    fun average(p: Picked, from: LocalDate, to: LocalDate, span: String, subject: String?, base: String): AssistantData {
        val days = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
        val sum = p.sum
        val perDay = sum / days
        val facts = buildString {
            append(base)
            appendLine("За $span${about(subject)} ($days ${daysWord(days)}): ${formatRub(sum)}.")
            appendLine("В среднем в день: ${formatRub(perDay)}, в неделю: ${formatRub(perDay * 7)}.")
            if (p.rows.isNotEmpty()) appendLine("Средняя покупка: ${formatRub(sum / p.rows.size)}.")
            p.notes().forEach(::appendLine)
        }
        return AssistantData(facts, formatRub(perDay), "в среднем в день · $span", ids(p.rows))
    }
}
