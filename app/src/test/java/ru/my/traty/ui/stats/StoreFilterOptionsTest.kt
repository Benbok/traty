package ru.my.traty.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.items.ItemGroupSale

class StoreFilterOptionsTest {

    private fun sale(
        name: String = "Молоко",
        place: String?,
        unitPriceKopecks: Long?,
        totalPriceKopecks: Long? = unitPriceKopecks,
    ) = ItemGroupSale(
        name = name,
        timestamp = 1_000L,
        place = place,
        unitPriceKopecks = unitPriceKopecks,
        totalPriceKopecks = totalPriceKopecks,
        quantityMilli = 1_000L,
        unit = "л",
        packMilli = null,
        packUnit = null,
    )

    @Test
    fun buildsStoreOptionsGroupedByPlaceWithMinAndMaxPrices() {
        val sales = listOf(
            sale(place = "Пятёрочка", unitPriceKopecks = 8_900),
            sale(place = "Пятёрочка", unitPriceKopecks = 9_500),
            sale(place = "Магнит", unitPriceKopecks = 10_500),
            sale(place = null, unitPriceKopecks = 7_000),
        )

        val options = StoreFilterOptions.build(sales)
        assertEquals(3, options.size)

        val pyat = options.first { it.place == "Пятёрочка" }
        assertEquals(2, pyat.count)
        assertEquals(8_900L, pyat.minPriceKopecks)
        assertEquals(9_500L, pyat.maxPriceKopecks)

        val magnit = options.first { it.place == "Магнит" }
        assertEquals(1, magnit.count)
        assertEquals(10_500L, magnit.minPriceKopecks)

        val noStore = options.first { it.place == null }
        assertEquals(NO_STORE_KEY, noStore.key)
        assertEquals("Без магазина", noStore.displayName)
    }

    @Test
    fun findsBestStoreKeyWhenMultipleStoresWithPricesExist() {
        val sales = listOf(
            sale(place = "Пятёрочка", unitPriceKopecks = 8_900),
            sale(place = "Магнит", unitPriceKopecks = 10_500),
            sale(place = "Перекрёсток", unitPriceKopecks = 12_000),
        )

        val options = StoreFilterOptions.build(sales)
        val best = StoreFilterOptions.findBestStoreKeys(options)
        assertEquals(setOf("Пятёрочка"), best)
    }

    @Test
    fun returnsEmptyBestStoresWhenOnlyOneStoreExists() {
        val sales = listOf(
            sale(place = "Пятёрочка", unitPriceKopecks = 8_900),
            sale(place = "Пятёрочка", unitPriceKopecks = 9_500),
        )

        val options = StoreFilterOptions.build(sales)
        val best = StoreFilterOptions.findBestStoreKeys(options)
        assertTrue(best.isEmpty())
    }

    @Test
    fun handlesTiedBestStores() {
        val sales = listOf(
            sale(place = "Пятёрочка", unitPriceKopecks = 8_900),
            sale(place = "Магнит", unitPriceKopecks = 8_900),
            sale(place = "Перекрёсток", unitPriceKopecks = 12_000),
        )

        val options = StoreFilterOptions.build(sales)
        val best = StoreFilterOptions.findBestStoreKeys(options)
        assertEquals(setOf("Пятёрочка", "Магнит"), best)
    }
}
