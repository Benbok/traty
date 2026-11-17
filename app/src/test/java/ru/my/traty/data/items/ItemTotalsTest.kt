package ru.my.traty.data.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemTotalsTest {
    private fun sale(
        name: String,
        price: Long?,
        expenseId: Long,
        place: String? = null,
        ts: Long = expenseId * 1000,
        qty: Long? = null,
        unit: String? = null,
    ) = ItemSale(name, price, qty, unit, expenseId, ts, place, categoryId = 1)

    private val eldorado = listOf(
        sale("телефон", 4_000_000, 1, "Эльдорадо"),
        sale("чехол", 200_000, 1, "Эльдорадо"),
        sale("клавиатура", 300_000, 1, "Эльдорадо"),
    )

    @Test
    fun productsSortedBySum() {
        val r = ItemTotals.byProduct(eldorado)
        assertEquals(listOf("Телефон", "Клавиатура", "Чехол"), r.map { it.name })
        assertEquals(4_000_000L, r.first().totalKopecks)
        assertEquals(listOf("Эльдорадо"), r.first().places)
    }

    @Test
    fun sameProductAcrossExpensesIsSummed() {
        val sales = listOf(
            sale("молоко", 9_000, 2, "Пятёрочка", qty = 1000, unit = "л"),
            sale("Молока", 11_000, 3, "пятерочка", qty = 2000, unit = "л"),
            sale("молоко", 10_000, 4, "Магнит", qty = 1000, unit = "л"),
        )
        val milk = ItemTotals.byProduct(sales).single()
        assertEquals(30_000L, milk.totalKopecks)
        assertEquals(3, milk.purchases)
        assertEquals("4 л", milk.amounts.known())
        // Последнее написание и магазины по сумме, «пятерочка» и «Пятёрочка» — один магазин
        assertEquals("Молоко", milk.name)
        assertEquals(2, milk.places.size)
        assertEquals("Магнит", milk.places[1])
    }

    @Test
    fun insideOneShopOnly() {
        val sales = eldorado + sale("телефон", 3_500_000, 5, "М.Видео")
        val inShop = ItemTotals.byProduct(sales, place = "эльдорадо")
        assertEquals(4_000_000L, inShop.first { it.name == "Телефон" }.totalKopecks)
        val all = ItemTotals.byProduct(sales)
        assertEquals(7_500_000L, all.first { it.name == "Телефон" }.totalKopecks)
        assertEquals(2, all.first { it.name == "Телефон" }.purchases)
    }

    @Test
    fun unpricedItemsCountedButNotSummed() {
        val sales = listOf(sale("хлеб", null, 6), sale("хлеб", 8_000, 7))
        val bread = ItemTotals.byProduct(sales).single()
        assertEquals(8_000L, bread.totalKopecks)
        assertEquals(1, bread.unpriced)
        assertEquals(2, bread.purchases)
    }

    @Test
    fun weightAndPiecesCountedSeparately() {
        val sales = listOf(sale("яблоки", 20_000, 8, qty = 1000, unit = "кг"), sale("яблоки", 5_000, 9, qty = 3000, unit = "шт"))
        assertEquals("3 шт · 1 кг", ItemTotals.byProduct(sales).single().amounts.known())
    }

    @Test
    fun packSizeMultipliesPiecesAndOldNamesWithPackAreOneProduct() {
        val sales = listOf(
            // Новая позиция: фасовка в своём поле, 3 шт по 200 мл
            ItemSale("Молочный коктейль", 27_000, 3000, "шт", 10, 10_000, "Пятёрочка", 1, packMilli = 200_000, packUnit = "мл"),
            // Старый чек: фасовка только в названии
            sale("Молочный коктейль 450мл", 12_000, 11),
        )
        val shake = ItemTotals.byProduct(sales).single()
        assertEquals(39_000L, shake.totalKopecks)
        assertEquals(2, shake.purchases)
        assertEquals("4 шт · 1,05 л", shake.amounts.known())
    }

    @Test
    fun nothingNamedShowsNoQuantity() {
        val sales = listOf(sale("хлеб", 5_000, 1), sale("хлеб", 5_000, 2))
        assertNull(ItemTotals.byProduct(sales).single().amounts.known())
    }

    @Test
    fun storesWithItems() {
        val sales = eldorado + sale("молоко", 9_000, 2, "Пятёрочка") + sale("хлеб", 5_000, 3, null)
        val stores = ItemTotals.stores(sales)
        assertEquals(listOf("Эльдорадо", "Пятёрочка"), stores.map { it.name })
        assertEquals(3, stores.first().products)
        assertEquals(4_500_000L, stores.first().totalKopecks)
    }

    @Test
    fun historyOfOneProductNewestFirst() {
        val sales = eldorado + sale("телефона", 3_500_000, 5, "М.Видео")
        val h = ItemTotals.history(sales, ItemNames.key("Телефон"))
        assertEquals(listOf(5L, 1L), h.map { it.expenseId })
    }
}
