package ru.my.traty.ai

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.IncomeSources

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
 * Запасной разбор без интернета: «кофе 250» → 250 ₽, категория по ключевым словам.
 * Работает, если ИИ недоступен или выбран режим «Без ИИ».
 */
object OfflineParser {
    private val numberRe = Regex("""(\d+(?:[.,]\d{1,2})?)""")

    fun parse(text: String, categories: List<CategoryWithSubs>): List<ParsedExpense> {
        val m = numberRe.find(text) ?: return emptyList()
        val amount = m.value.replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val note = text.removeRange(m.range).replace(Regex("""(руб(лей|ля|ль)?|₽|р\.)"""), "")
            .trim(' ', ',', '.').ifBlank { null }
        val (cat, sub) = guessCategory(note.orEmpty(), categories)
        return listOf(ParsedExpense(amount = amount, category = cat, subcategory = sub, note = note))
    }

    private fun guessCategory(text: String, categories: List<CategoryWithSubs>): Pair<String, String?> {
        val words = text.lowercase().split(Regex("""\s+""")).filter { it.length >= 3 }
        fun hits(label: String): Boolean {
            val stem = label.lowercase().take(4)
            return words.any { it.startsWith(stem) || stem.startsWith(it.take(4)) }
        }
        for (c in categories) {
            val name = c.category.name
            c.subNames().firstOrNull { hits(it) }?.let { return name to it }
            if (hits(name)) return name to null
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
