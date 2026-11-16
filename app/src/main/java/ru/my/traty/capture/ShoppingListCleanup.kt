package ru.my.traty.capture

import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ListedItem
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ParsedItem
import ru.my.traty.ai.PurchasePayment
import ru.my.traty.ai.PurchaseTermsParser
import ru.my.traty.ai.ShoppingList
import ru.my.traty.ai.ShoppingListFormat
import ru.my.traty.ai.ShoppingListParser
import ru.my.traty.ai.SpokenTotal
import ru.my.traty.data.sameName

/**
 * Список покупок в одной фразе — одна трата: «в продуктовом на 2500 купил рыбу, консерву, хлеб» →
 * 2500 ₽, заметка «рыбу, консерву, хлеб». Правила поверх ответа ИИ:
 * - ИИ разбил покупку на трату за каждый товар — склеиваем обратно (одна категория, одно место, один день);
 * - итог берётся из названной суммы («на 2500», «всего 2500»), а без неё — сложением цен (считает приложение);
 * - цены товаров не теряются: они остаются в заметке («рыбу 500 ₽, хлеб 80 ₽») и в позициях траты
 *   (товар, цена) — по ним потом считаются траты на каждый товар. Позиции — от двух товаров.
 * Несколько магазинов («Пятёрочка 1200, Магнит 800») и разные категории остаются отдельными тратами.
 */
object ShoppingListCleanup {

    fun apply(entries: List<ParsedEntry>, rawText: String): List<ParsedEntry> {
        if (entries.isEmpty() || entries.any { it.entryType != EntryType.EXPENSE || it.part != null }) return entries
        // «рассрочка на 6 месяцев», «аванс за холодильник» — не список покупок
        if (PurchaseTermsParser.find(rawText) != null || PurchasePayment.isPurchasePayment(rawText)) return entries
        val places = entries.mapNotNull { it.place?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }
        val list = ShoppingListParser.find(rawText, places)
        val total = SpokenTotal.find(rawText)?.value
        if (entries.size == 1) return listOf(refine(entries.single(), list, total))
        if (!canMerge(entries, places, list, total)) return entries
        return listOf(merge(entries, places.singleOrNull(), list, total))
    }

    /** Одна трата: итог из фразы и список товаров в заметке, если ИИ их потерял. */
    private fun refine(e: ParsedEntry, list: ShoppingList?, total: Double?): ParsedEntry {
        val amount = total ?: list?.amount ?: e.amount
        val note = when {
            list == null -> e.note
            e.note.isNullOrBlank() -> list.note()
            // ИИ потерял цены, которые были названы
            list.hasPrices && e.note.none(Char::isDigit) -> list.note()
            else -> e.note
        }
        return e.copy(amount = amount, note = note, items = itemsOf(e.items, list))
    }

    /**
     * Позиции траты: от ИИ, если он их дал; товары из самой фразы, если ИИ их не дал или потерял названные цены.
     * Меньше двух товаров — позиций нет, хватит заметки.
     */
    internal fun itemsOf(fromAi: List<ParsedItem>?, list: ShoppingList?): List<ParsedItem>? {
        val ai = fromAi?.takeIf { it.size >= 2 }
        val phrase = list?.items?.map { ParsedItem(name = it.name, price = it.price) }?.takeIf { it.size >= 2 }
        return when {
            ai == null -> phrase
            phrase != null && list?.hasPrices == true && ai.none { it.price != null } -> phrase
            else -> ai
        }
    }

    private fun canMerge(entries: List<ParsedEntry>, places: List<String>, list: ShoppingList?, total: Double?): Boolean {
        if (places.size > 1) return false
        if (entries.map { it.date }.distinct().size > 1) return false
        if (entries.map { it.category.trim().lowercase() }.distinct().size > 1) return false
        val sameSub = entries.all { it.subcategory != null && sameName(it.subcategory, entries.first().subcategory.orEmpty()) }
        val samePlace = places.size == 1 && entries.all { it.place != null }
        return total != null || list != null || samePlace || sameSub
    }

    private fun merge(entries: List<ParsedEntry>, place: String?, list: ShoppingList?, total: Double?): ParsedEntry {
        val first = entries.first()
        val sum = entries.sumOf { it.amount }
        // Список из самой фразы надёжнее. Без него берём товары от ИИ; их суммы — цены, только если итог не назван
        // (иначе ИИ мог разделить итог между товарами сам)
        val note = list?.note()
            ?: ShoppingListFormat.note(entries.map { e ->
                ListedItem(e.note?.takeIf { it.isNotBlank() } ?: e.subcategory ?: e.category, e.amount.takeIf { total == null })
            })
        val fromEntries = entries.map { e ->
            ParsedItem(name = e.note?.takeIf { it.isNotBlank() } ?: e.subcategory ?: e.category, price = e.amount.takeIf { total == null })
        }
        return first.copy(
            amount = total ?: sum,
            place = place,
            subcategory = entries.firstNotNullOfOrNull { it.subcategory },
            emoji = entries.firstNotNullOfOrNull { it.emoji },
            note = note,
            // Как и с заметкой: товары из самой фразы надёжнее, чем разбивка ИИ
            items = itemsOf(null, list) ?: fromEntries.takeIf { it.size >= 2 },
        )
    }
}
