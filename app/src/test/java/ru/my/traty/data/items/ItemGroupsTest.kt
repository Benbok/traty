package ru.my.traty.data.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemGroupsTest {
    private var nextExpense = 1L

    private fun sale(
        name: String,
        priceKopecks: Long? = null,
        qtyMilli: Long? = null,
        unit: String? = null,
        place: String? = null,
        timestamp: Long = 1_000L,
    ) = ItemSale(
        name = name,
        priceKopecks = priceKopecks,
        quantityMilli = qtyMilli,
        unit = unit,
        expenseId = nextExpense++,
        timestamp = timestamp,
        place = place,
        categoryId = 1L,
    )

    @Test
    fun groupsSimilarChocolateMilkNamesAcrossStores() {
        val groups = ItemGroups.build(
            listOf(
                sale("Шоколадное молоко Чудо", priceKopecks = 8_999, place = "Магнит"),
                sale("Чудо коктейль молочный шоколадный", priceKopecks = 9_999, place = "Пятёрочка"),
                sale("Коктейль молочный Чудо шоколадный", priceKopecks = 10_999, place = "Пятёрочка"),
            )
        )
        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(3, group.count)
        assertEquals(8_999L, group.minPriceKopecks)
        assertEquals(10_999L, group.maxPriceKopecks)
        assertEquals(listOf("Пятёрочка", "Магнит"), group.places)
    }

    @Test
    fun keepsDifferentFlavorsSeparate() {
        val groups = ItemGroups.build(
            listOf(
                sale("Чудо коктейль молочный шоколадный", priceKopecks = 9_900, place = "Магнит"),
                sale("Чудо коктейль молочный ванильный", priceKopecks = 8_900, place = "Магнит"),
            )
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun keepsUnrelatedSingleTokenNamesSeparate() {
        val groups = ItemGroups.build(
            listOf(
                sale("Хлеб", priceKopecks = 4_500),
                sale("Хлеб Бородинский нарезной заварной", priceKopecks = 7_839),
            )
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun computesUnitPriceForMultiPieceAndWeightItems() {
        val groups = ItemGroups.build(
            listOf(
                sale("Яблоки", priceKopecks = 10_000, qtyMilli = 500, unit = "кг", place = "Магнит"),
                sale("Яблоки", priceKopecks = 25_000, qtyMilli = 1_000, unit = "кг", place = "Пятёрочка"),
                sale("Хлеб", priceKopecks = 9_000, qtyMilli = 2_000, unit = "шт"),
                sale("Хлеб", priceKopecks = 4_500, qtyMilli = 1_000, unit = "шт"),
            )
        )
        val apples = groups.first { it.displayName == "Яблоки" }
        assertEquals(20_000L, apples.minPriceKopecks)
        assertEquals(25_000L, apples.maxPriceKopecks)
        assertEquals("кг", apples.priceUnit)

        val bread = groups.first { it.displayName == "Хлеб" }
        assertEquals(4_500L, bread.minPriceKopecks)
        assertEquals(4_500L, bread.maxPriceKopecks)
        assertNull(bread.priceUnit)
    }

    @Test
    fun handlesItemsWithoutPrice() {
        val groups = ItemGroups.build(
            listOf(sale("Пакет майка", priceKopecks = null, place = "магазин Пятёрочка"))
        )
        val group = groups.single()
        assertNull(group.minPriceKopecks)
        assertNull(group.maxPriceKopecks)
        assertEquals(listOf("Пятёрочка"), group.places)
    }

    @Test
    fun filterMatchesVariantNamesAndPlaces() {
        val groups = ItemGroups.build(
            listOf(
                sale("Шоколадное молоко Чудо", priceKopecks = 8_999, place = "Магнит"),
                sale("Чудо коктейль молочный шоколадный", priceKopecks = 9_999, place = "Пятёрочка"),
                sale("Чудо коктейль молочный шоколадный", priceKopecks = 10_999, place = "Пятёрочка"),
                sale("Хлеб Бородинский", priceKopecks = 7_839, place = "Лента"),
            )
        )
        // Ищем по слову из менее частого варианта названия и по названию магазина
        assertEquals(1, ItemGroups.filter(groups, "молоко").size)
        assertEquals(1, ItemGroups.filter(groups, "магнит").size)
        assertEquals(1, ItemGroups.filter(groups, "лента").size)
        assertEquals(2, ItemGroups.filter(groups, "").size)
    }

    @Test
    fun salesInsideGroupAreOrderedChronologicallyWithUnitAndTotalPrices() {
        val groups = ItemGroups.build(
            listOf(
                sale("Молоко Чудо", priceKopecks = 11_000, qtyMilli = 2_000, unit = "шт", place = "магазин Пятёрочка", timestamp = 3_000L),
                sale("Шоколадное молоко Чудо", priceKopecks = 4_500, qtyMilli = 1_000, unit = "шт", place = "Магнит", timestamp = 1_000L),
                sale("Чудо коктейль молочный шоколадный", priceKopecks = 6_000, qtyMilli = 1_000, unit = "шт", place = "Чижик", timestamp = 2_000L),
            )
        )
        // Молоко Чудо без слова шоколадный тоже проверяем отдельно, а тут 2 шоколадных в одной группе
        val choc = groups.first { "шоколад" in it.displayName.lowercase() }
        assertEquals(listOf(1_000L, 2_000L), choc.sales.map { it.timestamp })
        assertEquals(listOf("Магнит", "Чижик"), choc.sales.map { it.place })
        assertEquals(listOf(4_500L, 6_000L), choc.sales.map { it.unitPriceKopecks })
        assertEquals(10_500L, choc.totalSpentKopecks)

        val plain = groups.first { it.displayName == "Молоко Чудо" }
        assertEquals(5_500L, plain.sales.single().unitPriceKopecks)
        assertEquals(11_000L, plain.sales.single().totalPriceKopecks)
        assertEquals(11_000L, plain.totalSpentKopecks)
        assertEquals("Пятёрочка", plain.sales.single().place)
    }
}
