package ru.my.traty.capture

import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.sameName

/** Части одного чека, снятые сверху вниз. Повторы ищутся между снимками, не внутри них. */
object ReceiptParts {
    const val MAX_PHOTOS = 10

    data class Merged(val receipt: ParsedReceipt, val removed: Int, val notices: List<String>)

    fun merge(parts: List<ParsedReceipt>): Merged {
        require(parts.isNotEmpty())
        if (parts.mapNotNull { it.date }.distinct().size > 1) {
            throw ReceiptException("На снимках разные даты покупки. Проверьте, что это части одного чека")
        }
        val lines = mutableListOf<ReceiptLine>()
        val notices = mutableListOf<String>()
        var removed = 0
        parts.forEachIndexed { index, part ->
            val incoming = part.lines
            val previous = parts.getOrNull(index - 1)?.lines.orEmpty()
            val overlaps = (minOf(previous.size, incoming.size) downTo 1).asSequence().filter { size ->
                (0 until size).all { same(previous[previous.size - size + it], incoming[it]) }
            }.take(2).toList()
            val possibleOverlap = overlaps.firstOrNull() ?: 0
            val ambiguous = possibleOverlap > 0 &&
                (overlaps.size > 1 || incoming.take(possibleOverlap).all { same(incoming.first(), it) })
            val overlap = if (ambiguous) 0 else possibleOverlap
            if (ambiguous) {
                notices += "На стыке снимков ${index} и ${index + 1} есть одинаковые товары. Проверьте, сколько строк оставить"
            } else if (index > 0 && incoming.isNotEmpty() && overlap == 0) {
                notices += "Между снимками ${index} и ${index + 1} не найдено совпадение товаров: проверьте стык на пропуски и повторы"
            }
            removed += overlap
            lines += incoming.drop(overlap)
        }
        val places = parts.mapNotNull { it.place }
        if (places.any { !sameName(it, places.first()) }) notices += "Названия магазина на снимках различаются: проверьте, что это один чек"
        if (removed > 0) notices += "Исключены повторные строки между снимками: $removed. Проверьте количество одинаковых товаров"
        val first = parts.first()
        return Merged(
            first.copy(
                place = places.firstOrNull(),
                date = parts.firstNotNullOfOrNull { it.date },
                category = parts.firstNotNullOfOrNull { it.category },
                totalKopecks = parts.lastNotNullOfOrNull { it.totalKopecks },
                lines = lines,
            ), removed, notices,
        )
    }

    /** Без цены совпадение ненадёжно. Разные количества и фасовки — разные строки. */
    private fun same(a: ReceiptLine, b: ReceiptLine): Boolean =
        a.priceKopecks != null && a.priceKopecks == b.priceKopecks &&
            a.quantityMilli == b.quantityMilli && a.pack == b.pack &&
            a.unitPriceKopecks == b.unitPriceKopecks && a.discountKopecks == b.discountKopecks &&
            key(a.unit.orEmpty()) == key(b.unit.orEmpty()) && key(a.name).let { it.isNotEmpty() && it == key(b.name) }

    // В отличие от названия категории, товар сравнивается целиком, без ограничения в 40 символов.
    private val tokens = Regex("\\p{L}+|\\d+(?:[.,]\\d+)?")
    private fun key(text: String): String = tokens.findAll(text.lowercase().replace('ё', 'е'))
        .joinToString("|") { it.value.replace(',', '.') }

    private fun <T, R : Any> List<T>.lastNotNullOfOrNull(transform: (T) -> R?): R? =
        asReversed().firstNotNullOfOrNull(transform)
}
