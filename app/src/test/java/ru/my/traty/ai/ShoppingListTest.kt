package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.search.SearchText

class ShoppingListTest {

    @Test
    fun groceryListWithTotal() {
        val list = ShoppingListParser.find("в продуктовом на 2500 купил рыбу, консерву, ещё что-то")!!
        assertEquals(listOf(ListedItem("рыбу"), ListedItem("консерву")), list.items)
        assertEquals(2500.0, list.amount!!, 0.0)
        assertEquals("рыбу, консерву", list.note())
    }

    @Test
    fun pricesStayInNoteButTotalWins() {
        val list = ShoppingListParser.find("Пятёрочка рыба 500, хлеб за 80, молоко 2 пачки 150, всего 2500", places = listOf("Пятёрочка"))!!
        assertEquals("рыба 500 ₽, хлеб 80 ₽, молоко 2 пачки 150 ₽", list.note())
        assertEquals(2500.0, list.amount!!, 0.0)
    }

    @Test
    fun withoutTotalPricesAreAdded() {
        val list = ShoppingListParser.find("в пятёрочке хлеб 80, молоко 90 и сыр 400", places = listOf("Пятёрочка"))!!
        assertEquals(listOf("хлеб", "молоко", "сыр"), list.items.map { it.name })
        assertNull(list.total)
        assertEquals(570.0, list.amount!!, 0.0)
    }

    @Test
    fun totalBeforeListWithoutMarkerIsNotGuessedFromPrices() {
        // «2500» не помечено словом «всего»: сложением цен итог не подменяем
        val list = ShoppingListParser.find("2500 продуктовый рыба 500, хлеб 100")!!
        assertNull(list.amount)
    }

    @Test
    fun wordsTotalAndQuantityWords() {
        val list = ShoppingListParser.find("всего две пятьсот: купила одну рыбу, две булки, сметану")!!
        assertEquals(2500.0, list.total!!, 0.0)
        assertEquals("одну рыбу, две булки, сметану", list.note())
        assertFalse(list.hasPrices)
    }

    @Test
    fun yesterdayAndDateAreNotItems() {
        val list = ShoppingListParser.find("вчера в продуктовом на 1200 купил хлеб, кефир")!!
        assertEquals("хлеб, кефир", list.note())
    }

    @Test
    fun notAList() {
        assertNull(ShoppingListParser.find("зоомагазин вчера 731 лекарство для кошки Чуни от блох"))
        assertNull(ShoppingListParser.find("кофе 250"))
        // одна вещь и пустое слово — не список
        assertNull(ShoppingListParser.find("на 300 купил хлеб, ещё что-то"))
    }

    @Test
    fun spokenTotal() {
        assertEquals(2500.0, SpokenTotal.find("в продуктовом на 2500 купил рыбу")!!.value, 0.0)
        assertEquals(2500.0, SpokenTotal.find("рыба 500, хлеб 80, итого 2500")!!.value, 0.0)
        assertEquals(1800.0, SpokenTotal.find("чек на 1800 в Магните")!!.value, 0.0)
        assertNull(SpokenTotal.find("рыбу на 500 и хлеб 100"))
        assertNull(SpokenTotal.find("рассрочка на 6 месяцев 10000"))
        assertNull(SpokenTotal.find("на Озоне 2500"))
    }

    @Test
    fun rubles() {
        assertEquals("500", ShoppingListFormat.rub(500.0))
        assertEquals("1 500", ShoppingListFormat.rub(1500.0))
        assertEquals("99,50", ShoppingListFormat.rub(99.5))
    }

    @Test
    fun itemsAreFoundBySearch() {
        val note = ShoppingListParser.find("в продуктовом на 2500 купил рыбу, консерву, хлеб")!!.note()
        assertTrue(SearchText.matches("рыба", listOf(note)))
        assertTrue(SearchText.matches("консервы", listOf(note)))
    }
}
