package ru.my.traty.ui.home

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.search.SearchText
import ru.my.traty.ui.RU
import ru.my.traty.ui.formatRub

/**
 * Поиск по ленте «Траты». Ищем по тому, что видно у записи: категория, подкатегория, место,
 * заметка и сумма. Исходную фразу ([ru.my.traty.data.Expense.rawText]) не смотрим: одной фразой
 * часто надиктовывают несколько трат, и тогда поиск находил бы их все.
 *
 * Слова запроса сравниваются с учётом словоформ и в любом порядке ([SearchText]):
 * «лекарство кошке» находит трату с заметкой «лекарство для кошки Чуни от блох».
 */
object FeedSearch {

    fun matches(e: ExpenseWithCategory, query: String): Boolean =
        matches(query, e.expense.amountKopecks, listOf(e.categoryName, e.subcategoryName, e.expense.place, e.expense.note))

    fun matches(i: Income, query: String): Boolean =
        matches(query, i.amountKopecks, listOf(i.source, i.note))

    private fun matches(query: String, kopecks: Long, texts: List<String?>): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (amountMatches(kopecks, q.lowercase(RU))) return true
        val words = SearchText.queryWords(q)
        if (words.isEmpty()) return false // одни знаки, например «₽»
        // Число в запросе («такси 180») может быть суммой, остальные слова ищутся в тексте
        val (numbers, text) = words.partition { w -> w.all { it.isDigit() } }
        if (numbers.any { !amountMatches(kopecks, it) && !SearchText.matchesWords(listOf(it), texts) }) return false
        return SearchText.matchesWords(text, texts)
    }

    /**
     * Сумма ищется только по цифрам: «2500», «2 500» и «2 500 ₽» находят 2 500 ₽.
     * Пробелы в [formatRub] неразрывные, поэтому сравниваем без любых пробелов и знака рубля.
     */
    internal fun amountMatches(kopecks: Long, q: String): Boolean {
        val needle = q.compact()
        if (needle.isEmpty() || needle.none { it.isDigit() }) return false
        if (needle.any { !it.isDigit() && it != ',' && it != '.' }) return false
        return formatRub(kopecks).compact().contains(needle.replace('.', ','))
    }

    private fun String.compact(): String = filterNot { it.isWhitespace() || Character.isSpaceChar(it) || it == '₽' }
}
