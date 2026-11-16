package ru.my.traty.data.items

import ru.my.traty.data.Places

/**
 * Сколько ушло на один товар за период: «Телефон — 40 000 ₽, 1 покупка», «Молоко — 1 240 ₽, 8 покупок, 8 л».
 * [key] — ключ товара ([ItemNames.key]), [name] — последнее написание.
 * [purchases] — в скольких тратах встретился товар, [unpriced] — сколько раз без цены (в сумму не вошли).
 * [quantityMilli] и [unit] — общее количество, если у всех покупок оно названо в одних единицах; иначе null.
 * [places] — магазины, где его покупали, по убыванию суммы.
 */
data class ProductTotal(
    val key: String,
    val name: String,
    val totalKopecks: Long,
    val purchases: Int,
    val unpriced: Int,
    val quantityMilli: Long?,
    val unit: String?,
    val places: List<String>,
    val lastTimestamp: Long,
)

/** Магазин, в чьих тратах есть позиции: для выбора «товары внутри магазина». */
data class StoreItems(
    val key: String,
    val name: String,
    val totalKopecks: Long,
    val products: Int,
)

/** Подсчёт по товарам. Чистый Kotlin: данные из [ExpenseItemDao.getSales], результат для экрана и ассистента. */
object ItemTotals {

    /**
     * Товары за период по убыванию суммы. [place] — только позиции из этого магазина (в любом написании),
     * null — из всех. Позиции без названия пропускаются.
     */
    fun byProduct(sales: List<ItemSale>, place: String? = null): List<ProductTotal> {
        val placeKey = place?.let(Places::key)
        return sales
            .filter { placeKey == null || Places.key(it.place) == placeKey }
            .mapNotNull { s -> ItemNames.key(s.name).takeIf { it.isNotEmpty() }?.let { it to s } }
            .groupBy({ it.first }, { it.second })
            .map { (key, rows) -> total(key, rows) }
            .sortedWith(compareByDescending<ProductTotal> { it.totalKopecks }.thenByDescending { it.purchases }.thenBy { it.name })
    }

    /** Магазины, где есть позиции, по убыванию суммы позиций. Позиции без магазина не попадают. */
    fun stores(sales: List<ItemSale>): List<StoreItems> =
        sales.filter { Places.key(it.place).isNotEmpty() }
            .groupBy { Places.key(it.place) }
            .map { (key, rows) ->
                StoreItems(
                    key = key,
                    name = mostCommon(rows.mapNotNull { Places.normalize(it.place) }),
                    totalKopecks = rows.sumOf { it.priceKopecks ?: 0L },
                    products = rows.map { ItemNames.key(it.name) }.filter { it.isNotEmpty() }.distinct().size,
                )
            }
            .sortedWith(compareByDescending<StoreItems> { it.totalKopecks }.thenBy { it.name })

    /** Покупки одного товара по времени: для шторки «Телефон — где и почём». */
    fun history(sales: List<ItemSale>, productKey: String): List<ItemSale> =
        sales.filter { ItemNames.key(it.name) == productKey }.sortedByDescending { it.timestamp }

    private fun total(key: String, rows: List<ItemSale>): ProductTotal {
        val latest = rows.maxBy { it.timestamp }
        val units = rows.map { it.unit?.trim()?.lowercase() }.distinct()
        val quantity = if (rows.all { it.quantityMilli != null } && units.size == 1) rows.sumOf { it.quantityMilli ?: 0L } else null
        val byPlace = rows.filter { Places.key(it.place).isNotEmpty() }
            .groupBy { Places.key(it.place) }
            .map { (_, r) -> mostCommon(r.mapNotNull { Places.normalize(it.place) }) to r.sumOf { it.priceKopecks ?: 0L } }
            .sortedByDescending { it.second }
            .map { it.first }
        return ProductTotal(
            key = key,
            name = ItemNames.normalize(latest.name) ?: latest.name,
            totalKopecks = rows.sumOf { it.priceKopecks ?: 0L },
            purchases = rows.map { it.expenseId }.distinct().size,
            unpriced = rows.count { it.priceKopecks == null },
            quantityMilli = quantity,
            unit = if (quantity != null) rows.firstNotNullOfOrNull { it.unit?.trim()?.ifEmpty { null } } else null,
            places = byPlace,
            lastTimestamp = latest.timestamp,
        )
    }

    private fun mostCommon(names: List<String>): String =
        names.groupingBy { it }.eachCount().maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key
            ?: names.firstOrNull().orEmpty()
}
