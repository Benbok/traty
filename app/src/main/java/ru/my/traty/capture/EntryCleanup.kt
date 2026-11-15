package ru.my.traty.capture

import ru.my.traty.ai.EntryType
import ru.my.traty.ai.OfflineParser
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.PurchasePayment
import ru.my.traty.ai.ShopTypes
import ru.my.traty.ai.SpokenDateParser
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.sameName
import java.time.LocalDate

/**
 * Правила поверх ответа ИИ, чтобы фраза не терялась по частям:
 * - вид магазина («Зоомагазин») — не место, а подкатегория;
 * - подкатегория не повторяет категорию, а известная подкатегория подтягивает свою категорию вместо «Прочее»;
 * - заметка не начинается с места и не пропадает, если ИИ её не вернул;
 * - «вчера», «позавчера» в фразе с одной тратой считаются правилом, а не ИИ;
 * - «аванс за холодильник», «доплата» — трата, даже если ИИ решил, что деньги пришли;
 * - список покупок в одном магазине — одна трата с товарами в заметке ([ShoppingListCleanup]).
 */
object EntryCleanup {
    fun apply(entries0: List<ParsedEntry>, rawText: String, categories: List<CategoryWithSubs>, today: LocalDate): List<ParsedEntry> {
        val entries = ShoppingListCleanup.apply(entries0, rawText)
        val single = entries.size == 1
        return entries.map { e0 ->
            val e = if (single) paymentNotIncome(e0, rawText, categories, today) else e0
            if (e.entryType != EntryType.EXPENSE) return@map e
            var r = shopTypeAsSubcategory(e)
            r = fixSubcategory(r, categories)
            r = r.copy(note = cleanNote(r.note, r))
            if (single) {
                if (r.note == null) r = r.copy(note = noteFromPhrase(rawText, r, categories, today))
                SpokenDateParser.find(rawText, today)?.let { r = r.copy(date = it.date.toString()) }
            }
            r
        }
    }

    /**
     * ИИ записал «аванс за холодильник» или «пополнение за холодильник плюс 5000» как пришедшие деньги — это трата.
     * Категорию, место и заметку берём из простого разбора фразы, сумму и дату оставляем от ИИ.
     */
    private fun paymentNotIncome(e: ParsedEntry, rawText: String, categories: List<CategoryWithSubs>, today: LocalDate): ParsedEntry {
        if (e.entryType != EntryType.INCOME || !PurchasePayment.isPurchasePayment(rawText)) return e
        val guess = OfflineParser.parse(rawText, categories, today).firstOrNull()
        return e.copy(
            type = "expense",
            category = guess?.category ?: DefaultCategories.OTHER,
            emoji = guess?.emoji,
            subcategory = guess?.subcategory,
            place = e.place ?: guess?.place,
            note = e.note ?: guess?.note,
            source = null,
        )
    }

    private fun shopTypeAsSubcategory(e: ParsedEntry): ParsedEntry {
        val place = e.place ?: return e
        val type = ShopTypes.whole(place) ?: return e
        val category = if (isOther(e.category)) type.category else e.category
        val sub = e.subcategory?.takeIf { it.isNotBlank() }
            ?: type.subcategory.takeIf { sameName(category, type.category) }
        return e.copy(
            place = null,
            category = category,
            emoji = if (isOther(e.category)) type.emoji else e.emoji,
            subcategory = sub,
        )
    }

    private fun fixSubcategory(e: ParsedEntry, categories: List<CategoryWithSubs>): ParsedEntry {
        val sub = e.subcategory?.trim()?.takeIf { it.isNotEmpty() } ?: return e.copy(subcategory = null)
        if (sameName(sub, e.category)) return e.copy(subcategory = null)
        if (!isOther(e.category)) return e
        // ИИ не выбрал категорию, но подкатегория известна ровно в одной категории — берём её
        val owners = categories.filter { c -> c.subs.any { sameName(it.name, sub) } }
        val owner = owners.singleOrNull() ?: return e
        return e.copy(category = owner.category.name, emoji = null, subcategory = owner.subs.first { sameName(it.name, sub) }.name)
    }

    /** «Зоомагазин, лекарство…» → «лекарство…»: место и подкатегория уже лежат в своих полях. */
    private fun cleanNote(note: String?, e: ParsedEntry): String? {
        var n = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (prefix in listOfNotNull(e.place, e.subcategory)) {
            if (n.length > prefix.length && n.lowercase().startsWith(prefix.lowercase())) {
                n = n.substring(prefix.length).trim(' ', ',', '.', ';', ':', '-')
            }
        }
        if (listOfNotNull(e.place, e.subcategory, e.category).any { sameName(it, n) }) return null
        return n.ifEmpty { null }
    }

    /** ИИ не вернул заметку — берём остаток фразы (без суммы, даты, вида магазина и места). */
    private fun noteFromPhrase(rawText: String, e: ParsedEntry, categories: List<CategoryWithSubs>, today: LocalDate): String? {
        val rest = OfflineParser.parse(rawText, categories, today).firstOrNull()?.note ?: return null
        return cleanNote(stripWord(rest, e.place), e)
    }

    private fun stripWord(text: String, word: String?): String {
        if (word.isNullOrBlank()) return text
        val re = Regex("""(?<![\p{L}])(?:(?:в|во|из|на)\s+)?${Regex.escape(word)}(?![\p{L}])""", RegexOption.IGNORE_CASE)
        return text.replace(re, " ").replace(Regex("""\s+"""), " ").trim(' ', ',', '.', ';', ':', '-')
    }

    private fun isOther(name: String) = name.isBlank() || sameName(name, DefaultCategories.OTHER)
}
