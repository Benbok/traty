package ru.my.traty.data.items

import ru.my.traty.data.Places
import ru.my.traty.data.search.SearchText
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Одна покупка товара внутри [ItemGroup]: когда, где и по какой цене куплен товар.
 * [unitPriceKopecks] — цена за 1 единицу (шт / кг / л); если количество не указано, совпадает с [totalPriceKopecks].
 */
data class ItemGroupSale(
    val name: String,
    val timestamp: Long,
    val place: String?,
    val unitPriceKopecks: Long?,
    val totalPriceKopecks: Long?,
    val quantityMilli: Long?,
    val unit: String?,
    val packMilli: Long?,
    val packUnit: String?,
)

/**
 * Одна группа товаров за период: несколько похожих названий из разных магазинов объединены в одну строку.
 * [displayName] — самое частое написание в группе; [places] — уникальные магазины, отсортированные по числу продаж.
 * [minPriceKopecks] и [maxPriceKopecks] — диапазон цены за единицу (или позицию, если количество не указано)
 * за выбранный период; null — ни одна продажа не имеет цены.
 * [priceUnit] — единица цены («кг», «л»), если у всех ценовых позиций она одинакова и не является штучной; иначе null.
 * [variants] — все уникальные написания названий в группе (для поиска по любому из вариантов).
 * [sales] — покупки этой группы, отсортированные по времени (от ранних к поздним).
 */
data class ItemGroup(
    val displayName: String,
    val places: List<String>,
    val count: Int,
    val minPriceKopecks: Long?,
    val maxPriceKopecks: Long?,
    val totalSpentKopecks: Long = 0L,
    val priceUnit: String? = null,
    val variants: List<String> = listOf(displayName),
    val sales: List<ItemGroupSale> = emptyList(),
)

/**
 * Группировка товаров по схожести названий внутри одной категории.
 * Алгоритм: токенизируем «familyKey» каждого уникального названия, строим граф совпадений
 * (Jaccard ≥ [THRESHOLD], ≥ [MIN_SHARED_TOKENS] общих токенов и без конфликта вкусов/сортов),
 * связные компоненты — группы.
 */
object ItemGroups {

    /** Порог Jaccard: 0.35 — достаточно мягко для «Шоколадное молоко Чудо» ↔ «Чудо коктейль молочный шоколадный». */
    internal const val THRESHOLD = 0.35f

    /** Минимум общих значимых токенов (длиннее 2 символов, не чисто цифровых). */
    internal const val MIN_SHARED_TOKENS = 2

    private val FLAVOR_ROOTS = listOf(
        "шоколад", "ванил", "клубнич", "клубник", "банан", "карамел", "малин",
        "вишн", "апельсин", "лимон", "яблок", "груш", "персик", "манго",
        "пармез", "классич", "сырн", "сметан", "гриб", "лук", "чеснок", "остр",
    )

    private val PIECE_UNITS = setOf("шт", "штук", "штука", "штуки", "уп", "упак", "упаковка", "пач", "пачка", "бут", "бан", "пак")

    fun build(sales: List<ItemSale>): List<ItemGroup> {
        if (sales.isEmpty()) return emptyList()

        // 1. Уникальные нормализованные ключи → список всех продаж с таким ключом
        val byKey: Map<String, List<ItemSale>> = sales.groupBy { ItemNames.familyKey(it.name) }
            .filterKeys { it.isNotBlank() }

        val keys = byKey.keys.toList()
        val tokenSets: List<Set<String>> = keys.map { key ->
            key.split(' ').asSequence().filter { t -> (t.length > 2) && !t.all(Char::isDigit) }.toSet()
        }
        val flavorSets: List<Set<String>> = keys.map { key -> flavorsOf(key) }

        // 2. Union-Find для связных компонент
        val parent = IntArray(keys.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var n = x
            while (parent[n] != n) {
                val next = parent[n]
                parent[n] = r
                n = next
            }
            return r
        }
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }

        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                if (!flavorsCompatible(flavorSets[i], flavorSets[j])) continue
                val a = tokenSets[i]
                val b = tokenSets[j]
                val shared = a.intersect(b).size
                if (shared < MIN_SHARED_TOKENS) continue
                val jaccard = shared.toFloat() / (a.size + b.size - shared)
                if (jaccard >= THRESHOLD) union(i, j)
            }
        }

        // 3. Группируем по корню дерева
        val components: Map<Int, List<Int>> = keys.indices.groupBy { find(it) }

        return components.values.map { indices ->
            val groupSales = indices.flatMap { byKey.getValue(keys[it]) }
                .sortedWith(compareBy<ItemSale> { it.timestamp }.thenBy { it.expenseId })
            val displayName = mostFrequentName(groupSales)
            val places = placesOf(groupSales)
            val detailedSales = groupSales.map { sale ->
                ItemGroupSale(
                    name = sale.name,
                    timestamp = sale.timestamp,
                    place = Places.normalize(sale.place),
                    unitPriceKopecks = unitPriceKopecks(sale),
                    totalPriceKopecks = sale.priceKopecks,
                    quantityMilli = sale.quantityMilli,
                    unit = sale.unit,
                    packMilli = sale.packMilli,
                    packUnit = sale.packUnit,
                )
            }
            val priced = groupSales.mapNotNull { sale ->
                unitPriceKopecks(sale)?.let { price -> price to normalizedMeasureUnit(sale.unit) }
            }
            val units = priced.map { it.second }.distinct()
            val priceUnit = units.singleOrNull()
            val prices = priced.map { it.first }
            ItemGroup(
                displayName = displayName,
                places = places,
                count = groupSales.size,
                minPriceKopecks = prices.minOrNull(),
                maxPriceKopecks = prices.maxOrNull(),
                totalSpentKopecks = detailedSales.mapNotNull { it.totalPriceKopecks }.sum(),
                priceUnit = priceUnit,
                variants = groupSales.map { it.name }.distinct(),
                sales = detailedSales,
            )
        }.sortedWith(compareByDescending<ItemGroup> { it.count }.thenBy { it.displayName.lowercase() })
    }

    /** Поиск по названию товара (включая все варианты внутри группы) и ярлыкам магазинов. */
    fun filter(groups: List<ItemGroup>, query: String): List<ItemGroup> {
        if (query.isBlank()) return groups
        return groups.filter { g -> SearchText.matches(query, g.variants + g.places) }
    }

    /** Цена за 1 единицу (штуку / кг / л); при отсутствии количества возвращает полную цену позиции. */
    internal fun unitPriceKopecks(sale: ItemSale): Long? {
        val price = sale.priceKopecks ?: return null
        val qty = sale.quantityMilli?.takeIf { it > 0 } ?: return price
        if (qty == 1_000L) return price
        return BigDecimal(price).multiply(THOUSAND)
            .divide(BigDecimal(qty), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun normalizedMeasureUnit(raw: String?): String? {
        val u = raw?.trim()?.lowercase()?.trimEnd('.')?.ifEmpty { null } ?: return null
        return if (u in PIECE_UNITS) null else u
    }

    private fun flavorsOf(key: String): Set<String> {
        val words = key.split(' ')
        return words.mapNotNull { word ->
            val root = FLAVOR_ROOTS.firstOrNull { word.startsWith(it) } ?: return@mapNotNull null
            if (root == "клубник") "клубнич" else root
        }.toSet()
    }

    private fun flavorsCompatible(a: Set<String>, b: Set<String>): Boolean = a == b

    /** Самое частое написание среди всех продаж в группе (по [ItemSale.name]). */
    private fun mostFrequentName(sales: List<ItemSale>): String =
        sales.groupingBy { it.name }.eachCount().maxBy { it.value }.key

    /** Уникальные нормализованные магазины, отсортированные по количеству продаж (самый частый первый). */
    private fun placesOf(sales: List<ItemSale>): List<String> =
        sales.groupBy { Places.key(it.place) }
            .filterKeys { it.isNotEmpty() }
            .entries
            .sortedByDescending { it.value.size }
            .map { (_, v) -> Places.normalize(v.first().place) ?: v.first().place.orEmpty() }
            .filter { it.isNotBlank() }

    private val THOUSAND = BigDecimal(1000)
}
