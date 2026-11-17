package ru.my.traty.data.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemMeasureTest {
    @Test
    fun packInReceiptNames() {
        assertEquals(Pack(200_000, "мл"), PackSize.find("Коктейль молочный Чудо 2% 200мл"))
        assertEquals(Pack(500, "л"), PackSize.find("Вода питьевая 0,5 л"))
        assertEquals(Pack(1_500, "л"), PackSize.find("Кока-кола 1.5L"))
        assertEquals(Pack(500_000, "г"), PackSize.find("говядина гуляш 500 г"))
        assertEquals(Pack(900_000, "г"), PackSize.find("Гречка ядрица 900гр."))
        assertEquals(Pack(1_000, "кг"), PackSize.find("Сахар 1кг"))
        assertEquals(Pack(200_000, "мл"), PackSize.find("Сок 6х200мл"))
    }

    @Test
    fun notAPack() {
        assertNull(PackSize.find("Молоко 3,2%"))
        assertNull(PackSize.find("1 лимон"))
        assertNull(PackSize.find("Яйца С1 10 шт"))
        assertNull(PackSize.find("хлеб"))
        // Две разные фасовки — непонятно, какая
        assertNull(PackSize.find("молоко 1 л и сыр 200 г"))
    }

    @Test
    fun stripLeavesName() {
        assertEquals("Коктейль молочный Чудо 2%", PackSize.strip("Коктейль молочный Чудо 2% 200мл"))
        assertEquals("говядина гуляш", PackSize.strip("говядина гуляш 500 г"))
        assertEquals("хлеб", PackSize.strip("хлеб"))
        assertEquals("500 г", PackSize.strip("500 г"))
    }

    @Test
    fun parseTypedField() {
        assertEquals(Pack(200_000, "мл"), PackSize.parse("200 мл"))
        assertEquals(Pack(500, "л"), PackSize.parse("0,5л"))
        assertEquals(Pack(500_000, "г"), PackSize.parse("500 грамм"))
        assertNull(PackSize.parse("много"))
        assertNull(PackSize.parse(""))
    }

    @Test
    fun piecesTimesPack() {
        val a = ItemMeasure.of("Молочный коктейль", 3_000, "шт", 200_000, "мл")
        assertEquals(3_000L, a.piecesMilli)
        assertEquals(600_000L, a.mlMilli)
        assertEquals("3 шт · 600 мл", a.text())
    }

    @Test
    fun noQuantityIsOnePieceOfPack() {
        val a = ItemMeasure.of("говядина гуляш", null, null, 500_000, "г")
        assertEquals("1 шт · 500 г", a.text())
        assertEquals(1, a.assumedPieces)
    }

    @Test
    fun weightedGoodsHaveNoPieces() {
        val a = ItemMeasure.of("Бананы", 1_234, "кг", null, null)
        assertEquals(0L, a.piecesMilli)
        assertEquals("1,23 кг", a.text())
    }

    @Test
    fun packFromOldNameWhenFieldEmpty() {
        assertEquals("2 шт · 1 л", ItemMeasure.of("Сок 0,5 л", 2_000, "шт", null, null).text())
    }

    @Test
    fun otherUnitsKeptAsIs() {
        assertEquals("5 м", ItemMeasure.of("кабель", 5_000, "м", null, null).text())
    }

    @Test
    fun sumsAcrossPurchases() {
        val total = ItemMeasure.of("Коктейль", 2_000, "шт", 200_000, "мл") +
            ItemMeasure.of("Коктейль", null, null, 1_000, "л") +
            ItemMeasure.of("Коктейль", 1_000, "уп", null, null)
        assertEquals("4 шт · 1,4 л", total.text())
        assertEquals(1, total.assumedPieces)
    }

    @Test
    fun describeOnePosition() {
        assertEquals("3 шт по 200 мл", ItemMeasure.describe(3_000, "шт", Pack(200_000, "мл")))
        assertEquals("200 мл", ItemMeasure.describe(null, null, Pack(200_000, "мл")))
        assertEquals("200 мл", ItemMeasure.describe(1_000, "шт", Pack(200_000, "мл")))
        assertEquals("1,5 кг", ItemMeasure.describe(1_500, "кг", null))
        assertNull(ItemMeasure.describe(null, null, null))
    }

    @Test
    fun cleanMovesPackFromNameToField() {
        val item = ExpenseItemRepository.clean(listOf(ExpenseItem(name = "коктейль молочный 200мл", quantityMilli = 3_000, unit = "шт"))).single()
        assertEquals("Коктейль молочный", item.name)
        assertEquals(200_000L, item.packMilli)
        assertEquals("мл", item.packUnit)
        val typed = ExpenseItemRepository.clean(listOf(ExpenseItem(name = "Сок", packMilli = 1_000, packUnit = "литр"))).single()
        assertEquals("л", typed.packUnit)
        val plain = ExpenseItemRepository.clean(listOf(ExpenseItem(name = "хлеб"))).single()
        assertNull(plain.packMilli)
    }

    @Test
    fun packIsNotPartOfProductKey() {
        assertEquals(ItemNames.key("Коктейль молочный 200мл"), ItemNames.key("коктейль молочный 450 мл"))
        assertEquals(ItemNames.key("Коктейль молочный"), ItemNames.key("коктейль молочный 450 мл"))
    }
}
