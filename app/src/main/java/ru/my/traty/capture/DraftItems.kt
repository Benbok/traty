package ru.my.traty.capture

import kotlinx.serialization.Serializable
import ru.my.traty.ai.ParsedItem
import ru.my.traty.data.items.ExpenseItem
import java.math.BigDecimal
import kotlin.math.roundToLong

/**
 * Товар в черновике траты. Поля текстом, как и у [EntryRow]: так их правят на экране.
 * [priceText] — сколько заплачено за позицию целиком, пусто — цену не назвали.
 */
@Serializable
data class DraftItem(
    val name: String,
    val priceText: String = "",
    val qtyText: String = "",
    val unit: String = "",
) {
    fun priceKopecks(): Long? = parsePositiveKopecks(priceText)

    /** «2» → 2000, «1,5» → 1500; пусто или ноль — null. */
    fun quantityMilli(): Long? {
        val q = qtyText.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!q.isFinite() || q <= 0) return null
        return (q * 1000).roundToLong().takeIf { it > 0 }
    }

    fun toExpenseItem(): ExpenseItem = ExpenseItem(
        name = name, priceKopecks = priceKopecks(), quantityMilli = quantityMilli(), unit = unit.trim().ifEmpty { null },
    )

    companion object {
        fun fromParsed(i: ParsedItem) = DraftItem(
            name = i.name.trim(),
            priceText = i.price?.let { rublesText(it) }.orEmpty(),
            qtyText = i.qty?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
            unit = i.unit?.trim().orEmpty(),
        )

        fun fromSaved(i: ExpenseItem) = DraftItem(
            name = i.name,
            priceText = i.priceKopecks?.let(::kopecksToText).orEmpty(),
            qtyText = i.quantityMilli?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }.orEmpty(),
            unit = i.unit.orEmpty(),
        )

        private fun rublesText(rub: Double): String = kopecksToText((rub * 100).roundToLong())
    }
}
