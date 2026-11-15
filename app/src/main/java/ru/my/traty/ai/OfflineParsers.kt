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
        val amount = SpokenAmountParser.find(text) ?: return emptyList()
        val day = SpokenDateParser.find(text, today)
        val shop = ShopTypes.find(text)
        val note = leftover(text, listOfNotNull(amount.range, day?.range, shop?.range))
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

    private fun hits(label: String, words: List<String>): Boolean {
        val stem = label.lowercase().take(4)
        return words.any { it.startsWith(stem) || stem.startsWith(it.take(4)) }
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

/** Запасной разбор без интернета: «аванс 30 000» → 30000 ₽, источник по ключевым словам. */
object OfflineIncomeParser {
    // пробел, NBSP (U+00A0), узкий NBSP (U+202F)
    private const val SEP = "[   ]"

    // «80 000» (разделитель тысяч — пробел/NBSP/NNBSP между 3-значными группами) либо обычное число;
    // дробная часть через «,» или «.». Берётся только первое число в тексте.
    private val numberRe = Regex("""\d{1,3}(?:$SEP\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
    private val currencyRe = Regex("""(руб(лей|ля|ль)?|₽|р\.)""")

    fun parse(text: String): List<ParsedIncome> {
        val m = numberRe.find(text) ?: return emptyList()
        val amount = m.value.replace(Regex(SEP), "").replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val note = text.removeRange(m.range).replace(currencyRe, "")
            .trim(' ', ',', '.').ifBlank { null }
        return listOf(ParsedIncome(amount = amount, source = guessSource(text), note = note))
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
