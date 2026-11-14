package ru.my.traty.ui.home

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.ui.RU
import ru.my.traty.ui.formatRub

/**
 * Поиск по ленте «Траты». Ищем по тому, что видно у записи: категория, подкатегория, место,
 * заметка и сумма. Исходную фразу ([ru.my.traty.data.Expense.rawText]) не смотрим: одной фразой
 * часто надиктовывают несколько трат, и тогда поиск находил бы их все.
 */
object FeedSearch {

    fun matches(e: ExpenseWithCategory, query: String): Boolean {
        val q = query.trim().lowercase(RU)
        if (q.isEmpty()) return true
        return e.categoryName.has(q)
            || e.subcategoryName.has(q)
            || e.expense.place.has(q)
            || e.expense.note.has(q)
            || amountMatches(e.expense.amountKopecks, q)
    }

    fun matches(i: Income, query: String): Boolean {
        val q = query.trim().lowercase(RU)
        if (q.isEmpty()) return true
        return i.source.has(q) || i.note.has(q) || amountMatches(i.amountKopecks, q)
    }

    private fun String?.has(q: String): Boolean = this?.lowercase(RU)?.contains(q) == true

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
