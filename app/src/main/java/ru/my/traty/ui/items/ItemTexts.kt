package ru.my.traty.ui.items

import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.ItemSale
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.ui.stats.purchases
import java.math.BigDecimal

/** Тексты про товары: «3 покупки · 4 л · Пятёрочка, Магнит». */
object ItemTexts {
    /** 1500 → «1,5», 2000 → «2». */
    fun quantity(milli: Long): String =
        BigDecimal(milli).movePointLeft(3).stripTrailingZeros().toPlainString().replace('.', ',')

    fun quantity(milli: Long?, unit: String?): String? =
        milli?.let { listOfNotNull(quantity(it), unit?.trim()?.ifEmpty { null }).joinToString(" ") }

    /** Количество одной покупки с фасовкой: «3 шт по 200 мл», «500 г». */
    fun quantity(sale: ItemSale): String? =
        ItemMeasure.describe(sale.quantityMilli, sale.unit, ItemMeasure.packOf(sale.name, sale.packMilli, sale.packUnit))

    /** Подпись под товаром в списке. Магазинов — не больше двух, остальные «и ещё N». */
    fun subtitle(p: ProductTotal, showPlaces: Boolean = true): String {
        val parts = mutableListOf(purchases(p.purchases))
        p.amounts.known()?.let { parts += it }
        if (p.unpriced > 0) parts += "без цены: ${p.unpriced}"
        if (showPlaces && p.places.isNotEmpty()) {
            val shown = p.places.take(2).joinToString(", ")
            parts += if (p.places.size > 2) "$shown и ещё ${p.places.size - 2}" else shown
        }
        return parts.joinToString(" · ")
    }
}
