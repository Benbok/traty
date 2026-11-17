package ru.my.traty.data.items

import java.math.BigDecimal

/** Вес или объём в одних единицах: граммы или миллилитры, в тысячных (500 г = 500 000). */
data class Measure(val kind: Kind, val milli: Long) {
    enum class Kind { WEIGHT, VOLUME }
}

/**
 * Сколько товара куплено: штук, граммов, миллилитров. Складывается по покупкам.
 * [piecesMilli] — штуки и упаковки в тысячных; [gramsMilli] и [mlMilli] — вес и объём в тысячных грамма/мл;
 * [other] — количество в других единицах («м», «рул»), по единице; [assumedPieces] — позиции, где количество
 * не назвали и засчитали как одну штуку.
 */
data class ItemAmounts(
    val piecesMilli: Long = 0,
    val gramsMilli: Long = 0,
    val mlMilli: Long = 0,
    val other: Map<String, Long> = emptyMap(),
    val assumedPieces: Int = 0,
) {
    operator fun plus(o: ItemAmounts) = ItemAmounts(
        piecesMilli = piecesMilli + o.piecesMilli,
        gramsMilli = gramsMilli + o.gramsMilli,
        mlMilli = mlMilli + o.mlMilli,
        other = (other.keys + o.other.keys).associateWith { (other[it] ?: 0L) + (o.other[it] ?: 0L) },
        assumedPieces = assumedPieces + o.assumedPieces,
    )

    val isEmpty: Boolean get() = piecesMilli == 0L && gramsMilli == 0L && mlMilli == 0L && other.isEmpty()

    /** Ничего не названо: ни количества, ни веса, ни фасовки, только «по одной штуке» по умолчанию. */
    val onlyAssumed: Boolean
        get() = piecesMilli == assumedPieces * 1000L && gramsMilli == 0L && mlMilli == 0L && other.isEmpty()

    /** Подпись, если что-то названо: «3 шт · 0,6 л»; null — количество нигде не названо. */
    fun known(): String? = if (onlyAssumed || isEmpty) null else text()

    /** «3 шт · 0,6 л», «1,2 кг». Пусто — ничего не известно. */
    fun text(): String = parts().joinToString(" · ")

    fun parts(): List<String> = buildList {
        if (piecesMilli > 0) add("${ItemMeasure.number(piecesMilli)} шт")
        if (gramsMilli > 0) add(ItemMeasure.weight(gramsMilli))
        if (mlMilli > 0) add(ItemMeasure.volume(mlMilli))
        other.toSortedMap().forEach { (u, q) -> if (q > 0) add("${ItemMeasure.number(q)} $u") }
    }
}

/**
 * Количество и фасовка позиции. Чистый Kotlin: им пользуются статистика, ассистент и экраны.
 *
 * Как считается одна позиция:
 * - количество в весе или объёме («бананы 1,2 кг») — это и есть вес, штук нет;
 * - иначе штуки: названное количество («3 шт», «2 уп») или одна штука, если количество не назвали;
 * - фасовка («коктейль 200 мл») умножается на штуки: 3 шт по 200 мл = 0,6 л.
 * Фасовки нет в поле — ищется в названии («Коктейль молочный 200мл» из старых чеков).
 */
object ItemMeasure {
    private val PIECES = setOf("шт", "штук", "штука", "штуки", "уп", "упак", "упаковка", "упаковки", "пачка", "пачки", "пач", "бут", "бутылка", "банка", "пак")

    fun of(name: String, quantityMilli: Long?, unit: String?, packMilli: Long?, packUnit: String?): ItemAmounts {
        val qtyUnit = unit?.trim()?.lowercase()?.trimEnd('.')?.ifEmpty { null }
        val qtyMeasure = if (quantityMilli != null && qtyUnit != null) base(quantityMilli, qtyUnit) else null
        if (qtyMeasure != null) return amounts(qtyMeasure)
        if (quantityMilli != null && qtyUnit != null && qtyUnit !in PIECES) {
            return ItemAmounts(other = mapOf(qtyUnit to quantityMilli))
        }
        val pieces = quantityMilli ?: 1000L
        val assumed = if (quantityMilli == null) 1 else 0
        val pack = packOf(name, packMilli, packUnit)?.base()
            ?: return ItemAmounts(piecesMilli = pieces, assumedPieces = assumed)
        val total = multiply(pieces, pack.milli)
        return amounts(Measure(pack.kind, total)).copy(piecesMilli = pieces, assumedPieces = assumed)
    }

    fun of(i: ExpenseItem): ItemAmounts = of(i.name, i.quantityMilli, i.unit, i.packMilli, i.packUnit)

    fun of(s: ItemSale): ItemAmounts = of(s.name, s.quantityMilli, s.unit, s.packMilli, s.packUnit)

    /** Фасовка из поля, а если его нет — из названия. */
    fun packOf(name: String, packMilli: Long?, packUnit: String?): Pack? {
        val unit = packUnit?.let(PackSize::unitOf)
        if (packMilli != null && packMilli > 0 && unit != null) return Pack(packMilli, unit)
        return PackSize.find(name)
    }

    /** Вес или объём в граммах/мл; null — единица не вес и не объём («шт», «м»). */
    fun base(milli: Long, unit: String): Measure? = when (PackSize.unitOf(unit)) {
        "г" -> Measure(Measure.Kind.WEIGHT, milli)
        "кг" -> Measure(Measure.Kind.WEIGHT, milli * 1000)
        "мл" -> Measure(Measure.Kind.VOLUME, milli)
        "л" -> Measure(Measure.Kind.VOLUME, milli * 1000)
        else -> null
    }

    /**
     * Подпись количества у одной позиции: «3 шт по 200 мл», «200 мл», «1,2 кг», «2 уп».
     * null — ничего не названо.
     */
    fun describe(quantityMilli: Long?, unit: String?, pack: Pack?): String? {
        val qty = quantityMilli?.let { q -> listOfNotNull(number(q), unit?.trim()?.ifEmpty { null }).joinToString(" ") }
        return when {
            pack == null -> qty
            qty == null || quantityMilli == 1000L && (unit.isNullOrBlank() || unit.trim().lowercase() == "шт") -> pack.text()
            else -> "$qty по ${pack.text()}"
        }
    }

    /** 1500 → «1,5», 2000 → «2». */
    fun number(milli: Long): String =
        BigDecimal(milli).movePointLeft(3).stripTrailingZeros().toPlainString().replace('.', ',')

    /** Граммы в тысячных → «500 г» или «1,2 кг». */
    fun weight(gramsMilli: Long): String =
        if (gramsMilli >= 1_000_000) "${number(round(gramsMilli / 1000))} кг" else "${number(round(gramsMilli))} г"

    /** Миллилитры в тысячных → «200 мл» или «1,5 л». */
    fun volume(mlMilli: Long): String =
        if (mlMilli >= 1_000_000) "${number(round(mlMilli / 1000))} л" else "${number(round(mlMilli))} мл"

    private fun amounts(m: Measure): ItemAmounts =
        if (m.kind == Measure.Kind.WEIGHT) ItemAmounts(gramsMilli = m.milli) else ItemAmounts(mlMilli = m.milli)

    /** Штуки в тысячных × фасовка в тысячных → фасовка в тысячных. */
    private fun multiply(piecesMilli: Long, packMilli: Long): Long =
        BigDecimal(piecesMilli).multiply(BigDecimal(packMilli)).movePointLeft(3).toLong()

    /** Не больше двух знаков после запятой: «1,234 кг» → «1,23 кг». */
    private fun round(milli: Long): Long = (milli + 5) / 10 * 10
}
