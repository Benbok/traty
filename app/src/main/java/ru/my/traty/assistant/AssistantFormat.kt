package ru.my.traty.assistant

// Общие строки для фактов помощника: суммы со знаком, доли, разбивка по категориям.

import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun byCategory(rows: List<ExpenseWithCategory>): List<Pair<String, Long>> =
    rows.groupBy { it.categoryName }.map { (k, v) -> k to v.sumOf { it.expense.amountKopecks } }.sortedByDescending { it.second }

internal fun totals(rows: List<ExpenseWithCategory>): List<CategoryTotal> =
    rows.groupBy { it.expense.categoryId }.map { (id, v) -> CategoryTotal(id, v.first().categoryName, v.first().categoryEmoji, v.sumOf { it.expense.amountKopecks }) }

internal fun incomesIn(ctx: AssistantContext, from: LocalDate, to: LocalDate) = ctx.incomes.filter {
    it.origin != IncomeOrigin.RECONCILE && day(it.timestamp, ctx.zone).let { d -> !d.isBefore(from) && !d.isAfter(to) }
}

internal fun describe(r: ExpenseWithCategory, zone: ZoneId): String {
    val what = listOfNotNull(r.categoryName, r.subcategoryName, r.expense.place, r.expense.note).distinct().joinToString(", ")
    return "${date(day(r.expense.timestamp, zone))}: ${formatRub(r.expense.amountKopecks)}, $what"
}

internal fun ids(rows: List<ExpenseWithCategory>) =
    rows.sortedByDescending { it.expense.timestamp }.take(AssistantEngine.MAX_SHOWN_IDS).map { it.expense.id }

internal fun share(part: Long, total: Long) = if (total <= 0) "0%" else "${(part * 100.0 / total).roundToInt()}%"

internal fun signed(k: Long) = when {
    k > 0 -> "+" + formatRub(k)
    k < 0 -> "−" + formatRub(abs(k))
    else -> formatRub(0)
}

internal fun percent(diff: Long, base: Long) = if (base == 0L) null else "${if (diff > 0) "+" else if (diff < 0) "−" else ""}${abs(diff * 100.0 / base).roundToInt()}%"

internal fun daysWord(n: Int) = if (n % 10 == 1 && n % 100 != 11) "день" else if (n % 10 in 2..4 && n % 100 !in 12..14) "дня" else "дней"
