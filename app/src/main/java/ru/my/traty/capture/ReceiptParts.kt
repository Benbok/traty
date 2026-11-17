package ru.my.traty.capture

import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.ai.receipt.ReceiptText
import ru.my.traty.data.sameName

/** Части одного чека, снятые сверху вниз. Повторы ищутся между снимками, не внутри них. */
object ReceiptParts {
    const val MAX_PHOTOS = 10
    const val MAX_CHARS = 32_000

    data class Merged(val receipt: ParsedReceipt, val removed: Int, val notices: List<String>)

    /** Не обрезаем длинный чек молча: иначе конец с товарами и итогом потеряется. */
    fun clean(texts: List<String>): List<String> {
        if (texts.isEmpty() || texts.size > MAX_PHOTOS) throw ReceiptException("Добавьте от 1 до $MAX_PHOTOS снимков одного чека")
        val cleaned = texts.map(ReceiptText::clean)
        if (cleaned.any { it.length < 10 }) throw ReceiptException("На одном из снимков не нашлось текста чека. Удалите его и переснимите ближе")
        if (cleaned.sumOf { it.length } > MAX_CHARS) throw ReceiptException("Слишком много текста. Удалите лишние снимки или повторы одного участка чека")
        return cleaned
    }

    fun merge(parts: List<ParsedReceipt>, texts: List<String> = emptyList()): Merged {
        require(parts.isNotEmpty())
        require(texts.isEmpty() || texts.size == parts.size)
        if (parts.mapNotNull { it.date }.distinct().size > 1) {
            throw ReceiptException("На снимках разные даты покупки. Проверьте, что это части одного чека")
        }
        val lines = mutableListOf<ReceiptLine>()
        val notices = mutableListOf<String>()
        var removed = 0
        parts.forEachIndexed { index, part ->
            val incoming = part.lines
            // Только одинаковый текст всего снимка позволяет убрать повтор не у стыка:
            // одинаковый товар в середине чека сам по себе не доказывает повтор снимка.
            val repeatedPhoto = texts.isNotEmpty() && texts.take(index).any { key(it) == key(texts[index]) }
            val overlap = if (repeatedPhoto) incoming.size else
                (minOf(lines.size, incoming.size) downTo 1).firstOrNull { size ->
                    (0 until size).all { same(lines[lines.size - size + it], incoming[it]) }
                } ?: 0
            if (index > 0 && incoming.isNotEmpty() && overlap == 0) {
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
            key(a.unit.orEmpty()) == key(b.unit.orEmpty()) && key(a.name) == key(b.name)

    // В отличие от названия категории, товар сравнивается целиком, без ограничения в 40 символов.
    private val tokens = Regex("\\p{L}+|\\d+(?:[.,]\\d+)?")
    private fun key(text: String): String = tokens.findAll(text.lowercase().replace('ё', 'е'))
        .joinToString("|") { it.value.replace(',', '.') }

    private fun <T, R : Any> List<T>.lastNotNullOfOrNull(transform: (T) -> R?): R? =
        asReversed().firstNotNullOfOrNull(transform)
}
