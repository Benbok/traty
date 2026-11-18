package ru.my.traty.assistant

// Вопросы про товар внутри покупок: «сколько я потратил на шоколадки».
// Считается цена самих позиций, а не вся сумма чека, в котором они были.

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemAmounts
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.PackSize
import ru.my.traty.data.search.SearchText
import ru.my.traty.ui.formatRub
import java.time.ZoneId

/** Фраза есть в названии позиции: «шоколадки» находит «Шоколад Алёнка» и «шоколадку», «колу» — «Кока-кола». */
internal fun ExpenseItem.mentionsItem(phrase: String): Boolean {
    val words = stems(phrase)
    if (words.isEmpty()) return false
    val text = fold(name)
    return words.all { it in text } || SearchText.matches(phrase, listOf(name))
}

/**
 * Сколько из одной траты засчитать в ответ про товар.
 * [amount] — копейки; [items] — совпавшие позиции; [whole] — трата засчитана целиком
 * (у неё нет списка товаров, а товар назван в заметке или месте, либо в чеке только этот товар);
 * [unpriced] — совпавшие позиции без цены, в сумму они не вошли.
 * Количество товара (штуки, кг, литры) считает [AssistantItems.quantity].
 */
internal class ItemPart(
    val amount: Long,
    val items: List<ExpenseItem>,
    val whole: Boolean,
    val unpriced: Int = 0,
    /** Заметка траты без списка товаров: в ней может быть фасовка («гуляш 500 г»). */
    val note: String? = null,
)

internal data class ItemQuantity(val headline: String, val line: String)

internal object AssistantItems {
    /**
     * Доля траты для товаров [terms]; null — трата к ним не относится.
     * Если у траты есть позиции, решают они: в заметке такой покупки обычно тот же список, и по ней
     * засчиталась бы вся сумма чека. По заметке и месту ищем только у трат без позиций.
     */
    fun part(
        r: ExpenseWithCategory,
        items: List<ExpenseItem>,
        terms: List<String>,
        useSearchWords: Boolean = false,
        mentions: (String) -> Boolean,
    ): ItemPart? {
        if (terms.isEmpty()) return null
        if (items.isEmpty()) {
            return if (terms.any(mentions)) ItemPart(r.expense.amountKopecks, emptyList(), whole = true, note = r.expense.note) else null
        }
        return partForPositions(r, items) { item ->
            terms.any { phrase ->
                if (useSearchWords) SearchText.matches(phrase, listOf(item.name)) else item.mentionsItem(phrase)
            }
        }
    }

    fun partForPositions(r: ExpenseWithCategory, items: List<ExpenseItem>, matches: (ExpenseItem) -> Boolean): ItemPart? {
        val matched = items.filter(matches)
        if (matched.isEmpty()) return null
        // В чеке только этот товар — это и есть вся покупка, даже если цену у позиции не записали
        if (matched.size == items.size) return ItemPart(r.expense.amountKopecks, matched, whole = false)
        val priced = matched.mapNotNull { it.priceKopecks }
        return ItemPart(priced.sum(), matched, whole = false, unpriced = matched.size - priced.size)
    }

    /**
     * Пояснения к сумме по товару: сколько позиций, что не посчитано и что засчитано целиком.
     * Пусто, если вопрос не про товар.
     */
    fun notes(parts: List<ItemPart>, includeQuantity: Boolean = true): List<String> {
        if (parts.isEmpty()) return emptyList()
        val withItems = parts.filter { it.items.isNotEmpty() }
        val out = ArrayList<String>()
        if (withItems.isNotEmpty()) {
            out += "Это цена самих товаров, без остальных покупок в тех же чеках: позиций ${withItems.sumOf { it.items.size }} в ${withItems.size} ${purchasesWord(withItems.size)}."
        }
        if (includeQuantity) quantity(parts)?.let(out::add)
        val unpriced = parts.sumOf { it.unpriced }
        if (unpriced > 0) out += "У $unpriced ${positionsWord(unpriced)} не записана цена, в сумму они не вошли."
        val whole = parts.filter { it.whole }
        if (whole.isNotEmpty()) {
            out += "${whole.size} ${purchasesWord(whole.size, nominative = true)} без списка товаров ${if (whole.size == 1) "посчитана" else "посчитаны"} целиком " +
                "(${formatRub(whole.sumOf { it.amount })}): товар назван в заметке или месте."
        }
        return out
    }

    /**
     * Сколько товара куплено: «Количество: 5 шт · 1 л». Считает приложение по позициям ([ItemMeasure]):
     * 3 шт по 200 мл = 0,6 л, весовой товар — по весу. У покупки без списка товаров берётся фасовка
     * из заметки («гуляш 500 г»), а если её нет — такая покупка в количество не входит, об этом пишем.
     */
    fun quantity(parts: List<ItemPart>): String? = quantitySummary(parts)?.line

    fun quantitySummary(parts: List<ItemPart>): ItemQuantity? {
        if (parts.isEmpty()) return null
        var total = ItemAmounts()
        var unknown = 0
        var piecesWithoutMeasure = 0L
        parts.forEach { p ->
            when {
                p.items.isNotEmpty() -> p.items.forEach {
                    val amount = ItemMeasure.of(it)
                    total += amount
                    if (amount.mlMilli == 0L && amount.gramsMilli == 0L) {
                        piecesWithoutMeasure += amount.piecesMilli
                    }
                }
                PackSize.find(p.note) != null -> total += ItemMeasure.of(p.note.orEmpty(), null, null, null, null)
                else -> unknown++
            }
        }
        if (total.isEmpty) return null
        val headline = total.text()
        val line = buildString {
            append("Количество: $headline")
            if (total.assumedPieces > 0) {
                append(" (у ${total.assumedPieces} ${positionsWord(total.assumedPieces)} количество не названо, ${if (total.assumedPieces == 1) "посчитана" else "посчитаны"} как 1 шт)")
            }
            append('.')
            if (piecesWithoutMeasure > 0 && total.mlMilli > 0) {
                append(" У ${ItemMeasure.number(piecesWithoutMeasure)} шт. объём упаковки не указан.")
            }
            if (piecesWithoutMeasure > 0 && total.gramsMilli > 0) {
                append(" У ${ItemMeasure.number(piecesWithoutMeasure)} шт. вес упаковки не указан.")
            }
            if (unknown > 0) append(" Ещё в $unknown ${purchasesWord(unknown)} без списка товаров количество не названо, они не вошли.")
        }
        return ItemQuantity(headline, line)
    }

    /** Строка для списка: сами товары с ценами, а не вся сумма чека. */
    fun line(r: ExpenseWithCategory, part: ItemPart?, zone: ZoneId): String {
        if (part == null || part.items.isEmpty()) return describe(r, zone)
        val goods = part.items.joinToString(", ") { i ->
            val amount = ItemMeasure.describe(i.quantityMilli, i.unit, ItemMeasure.packOf(i.name, i.packMilli, i.packUnit))?.let { " $it" }.orEmpty()
            i.name.take(40) + amount + (i.priceKopecks?.let { " ${formatRub(it)}" } ?: " (без цены)")
        }
        val where = r.expense.place?.trim()?.take(40)?.takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty()
        return "${date(day(r.expense.timestamp, zone))}: $goods$where"
    }

    private fun purchasesWord(n: Int, nominative: Boolean = false): String = when {
        n % 10 == 1 && n % 100 != 11 -> if (nominative) "покупка" else "покупке"
        nominative && n % 10 in 2..4 && n % 100 !in 12..14 -> "покупки"
        nominative -> "покупок"
        else -> "покупках"
    }

    private fun positionsWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "позиции" else "позиций"
}
