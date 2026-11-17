package ru.my.traty.capture

import kotlinx.serialization.Serializable
import ru.my.traty.ai.ParsedItem
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.Pack
import ru.my.traty.data.items.PackSize
import java.math.BigDecimal
import kotlin.math.roundToLong

/**
 * Товар в черновике траты. Поля текстом, как и у [EntryRow]: так их правят на экране.
 * [priceText] — сколько заплачено за позицию целиком, пусто — цену не назвали.
 * [packText] — фасовка одной штуки («200 мл», «500 г»), пусто — не названа (тогда её ищут в названии при сохранении).
 */
@Serializable
data class DraftItem(
    val name: String,
    val priceText: String = "",
    val qtyText: String = "",
    val unit: String = "",
    val packText: String = "",
) {
    fun priceKopecks(): Long? = parsePositiveKopecks(priceText)

    /** «2» → 2000, «1,5» → 1500; пусто или ноль — null. */
    fun quantityMilli(): Long? {
        val q = qtyText.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!q.isFinite() || q <= 0) return null
        return (q * 1000).roundToLong().takeIf { it > 0 }
    }

    /** Фасовка из своего поля; null — поле пустое или не разобралось. */
    fun pack(): Pack? = PackSize.parse(packText)

    /** Подпись под названием: «3 шт по 200 мл», «500 г»; null — ничего не названо. */
    fun amountText(): String? = ItemMeasure.describe(quantityMilli(), unit.trim().ifEmpty { null }, pack())

    fun toExpenseItem(): ExpenseItem {
        val pack = pack()
        return ExpenseItem(
            name = name, priceKopecks = priceKopecks(), quantityMilli = quantityMilli(), unit = unit.trim().ifEmpty { null },
            packMilli = pack?.milli, packUnit = pack?.unit,
        )
    }

    companion object {
        fun fromParsed(i: ParsedItem): DraftItem {
            val (name, pack) = splitPack(i.name.trim(), PackSize.parse(i.pack))
            return DraftItem(
                name = name,
                priceText = i.price?.let { rublesText(it) }.orEmpty(),
                qtyText = i.qty?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
                unit = i.unit?.trim().orEmpty(),
                packText = pack?.text().orEmpty(),
            )
        }

        /**
         * Название и фасовка: фасовка из своего поля, а если его нет — из названия («коктейль 200 мл»),
         * и тогда название остаётся без неё. Так в окне проверки «Коктейль, 200 мл», а не «Коктейль 200 мл, 200 мл».
         */
        fun splitPack(name: String, given: Pack?): Pair<String, Pack?> {
            val pack = given ?: PackSize.find(name) ?: return name to null
            return PackSize.strip(name) to pack
        }

        fun fromSaved(i: ExpenseItem) = DraftItem(
            name = i.name,
            priceText = i.priceKopecks?.let(::kopecksToText).orEmpty(),
            qtyText = i.quantityMilli?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }.orEmpty(),
            unit = i.unit.orEmpty(),
            packText = ItemMeasure.packOf("", i.packMilli, i.packUnit)?.text().orEmpty(),
        )

        private fun rublesText(rub: Double): String = kopecksToText((rub * 100).roundToLong())
    }
}
