package ru.my.traty.ai

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.IncomeSources
import ru.my.traty.data.sameName
import java.time.LocalDate

/** Результат простого разбора траты без ИИ. */
data class ParsedExpense(
    val amount: Double = 0.0,
    val category: String = DefaultCategories.OTHER,
    val emoji: String? = null,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** YYYY-MM-DD или null (= сегодня) */
    val date: String? = null,
)

/** Результат простого разбора пополнения без ИИ. */
data class ParsedIncome(
    val amount: Double = 0.0,
    val source: String = IncomeSources.DEFAULT,
    val note: String? = null,
    /** YYYY-MM-DD или null (= сегодня) */
    val date: String? = null,
)

/**
 * Запасной разбор без интернета: «зоомагазин вчера 731 лекарство для кошки» → 731 ₽, вчера,
 * «Питомцы › Зоомагазин», заметка «лекарство для кошки». Работает, если ИИ недоступен или выбран режим «Без ИИ».
 */
object OfflineParser {
    private val currencyRe = Regex("""(?<![\p{L}])(руб(лей|ля|ль)?|р\.)(?![\p{L}])|₽""", RegexOption.IGNORE_CASE)

    fun parse(text: String, categories: List<CategoryWithSubs>, today: LocalDate = LocalDate.now()): List<ParsedExpense> {
        val spoken = SpokenAmountParser.find(text) ?: return emptyList()
        val day = SpokenDateParser.find(text, today)
        val market = Marketplaces.find(text)
        val shop = if (market == null) ShopTypes.find(text) else null
        // «в продуктовом на 2500 купил рыбу, консерву, хлеб» — одна трата на 2500, товары в заметке
        val total = SpokenTotal.find(text)
        val list = ShoppingListParser.find(text)
        val amount = total
            ?: list?.amount?.let { SpokenAmount(it, spoken.range) }
            ?: spoken
        val note = list?.note()
            ?: leftover(text, listOfNotNull(amount.range, day?.range, shop?.range, market?.range))
                ?.let { if (total != null) ShoppingListParser.tidy(it) else it }
        if (market != null) {
            val existing = categories.firstOrNull { sameName(it.category.name, Marketplaces.CATEGORY) }
            return listOf(
                ParsedExpense(
                    amount = amount.value, category = existing?.category?.name ?: Marketplaces.CATEGORY,
                    emoji = if (existing == null) Marketplaces.EMOJI else null,
                    subcategory = market.name, place = market.name, note = note, date = day?.date?.toString(),
                )
            )
        }
        val (cat, emoji, sub) = pickCategory(shop?.type, note.orEmpty(), categories)
        return listOf(
            ParsedExpense(
                amount = amount.value, category = cat, emoji = emoji, subcategory = sub,
                note = note, date = day?.date?.toString(),
            )
        )
    }

    /** Текст без суммы, даты и вида магазина — это заметка. Слова и регистр сохраняются: «кошки Чуни». */
    internal fun leftover(text: String, cut: List<IntRange>): String? {
        val sb = StringBuilder(text)
        cut.sortedByDescending { it.first }.forEach { r -> sb.replace(r.first, r.last + 1, " ") }
        return sb.toString().replace(currencyRe, " ")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([,.;:])"""), "$1")
            .trim(' ', ',', '.', ';', ':', '-')
            .ifBlank { null }
    }

    /** (категория, emoji для новой, подкатегория). Вид магазина главнее, потом слова о покупке, потом названия категорий. */
    private fun pickCategory(shop: ShopType?, note: String, categories: List<CategoryWithSubs>): Triple<String, String?, String?> {
        val type = shop ?: ShopTypes.hint(note)
        if (type != null) return resolve(type, note, categories)
        val (cat, sub) = guessCategory(note, categories)
        return Triple(cat, null, sub)
    }

    private fun resolve(type: ShopType, note: String, categories: List<CategoryWithSubs>): Triple<String, String?, String?> {
        val existing = categories.firstOrNull { sameName(it.category.name, type.category) }
            ?: return Triple(type.category, type.emoji, type.subcategory)
        val subName = type.subcategory?.let { s -> existing.subNames().firstOrNull { sameName(it, s) } ?: s }
            ?: guessSub(note, existing)
        return Triple(existing.category.name, null, subName)
    }

    private fun words(text: String) = text.lowercase().split(Regex("""\s+""")).filter { it.length >= 3 }

    /**
     * Слово фразы начинается с первых четырёх букв названия: «бензина» → «Бензин».
     * Короткое название («ТО», «НМО») совпадает только целиком: иначе «торт» попадал в «ТО»,
     * а предлог «под» — в «Подписки».
     */
    private fun hits(label: String, words: List<String>): Boolean {
        val name = label.lowercase()
        if (name.length < 4) return words.any { it == name }
        val stem = name.take(4)
        return words.any { it.startsWith(stem) }
    }

    private fun guessSub(text: String, category: CategoryWithSubs): String? {
        val w = words(text)
        return category.subNames().firstOrNull { hits(it, w) }
    }

    private fun guessCategory(text: String, categories: List<CategoryWithSubs>): Pair<String, String?> {
        val w = words(text)
        for (c in categories) {
            val name = c.category.name
            c.subNames().firstOrNull { hits(it, w) }?.let { return name to it }
            if (hits(name, w)) return name to null
        }
        return DefaultCategories.OTHER to null
    }
}

/** Запасной разбор без интернета: «аванс 30 000», «премия двадцать тысяч» → сумма, источник по ключевым словам. */
object OfflineIncomeParser {
    private val currencyRe = Regex("""(руб(лей|ля|ль)?|₽|р\.)""")

    /** Сумма — первое число цифрами («30 000», «20 тысяч») или словами, как у трат ([SpokenAmountParser]). */
    fun parse(text: String): List<ParsedIncome> {
        val m = SpokenAmountParser.find(text) ?: return emptyList()
        val note = text.removeRange(m.range).replace(currencyRe, "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.').ifBlank { null }
        return listOf(ParsedIncome(amount = m.value, source = guessSource(text), note = note))
    }

    private fun guessSource(text: String): String {
        val t = text.lowercase()
        return when {
            "зарплат" in t -> "Зарплата"
            "аванс" in t -> "Аванс"
            "дежур" in t -> "Дежурства"
            "возврат" in t || "вернул" in t -> "Возврат"
            "подработ" in t -> "Подработка"
            else -> IncomeSources.DEFAULT
        }
    }
}
