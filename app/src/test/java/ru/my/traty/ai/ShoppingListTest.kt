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
    fun pricesWithKopecksAfterDash() {
        // Фраза с телефона: цены вида «83—90» — это 83,90, и итог складывается из них
        val phrase = "Аа Гречка фасованная — 83—90, горошек зелёный 99, Фруктовое яблоко — это сок 69—90, оливки зелёные — 91—90"
        val list = ShoppingListParser.find(phrase)!!
        assertEquals(listOf(83.9, 99.0, 69.9, 91.9), list.items.map { it.price })
        assertTrue(list.pricesCoverPhrase)
        assertEquals(344.7, list.amount!!, 1e-9)
        assertTrue(list.note(), "83,90 ₽" in list.note())
    }

    @Test
    fun decimalCommaIsNotAListSeparator() {
        // «69,90» — одна цена, а не два куска списка; «83, 90» с пробелом по-прежнему делит список
        val list = ShoppingListParser.find("сок 69,90, хлеб 45")!!
        assertEquals(listOf(ListedItem("сок", 69.9), ListedItem("хлеб", 45.0)), list.items)
    }

    @Test
    fun priceSplitOffByCommaBelongsToPreviousItem() {
        // Текст GigaAM с телефона: запятая между товаром и его ценой, цена с десятичной запятой
        val phrase = "Пам Гречка фасованная 900 г, 83—90, горшочек зелёный 99, Фруктово-яблоко сок 69,90"
        val list = ShoppingListParser.find(phrase)!!
        assertEquals(listOf(83.9, 99.0, 69.9), list.items.map { it.price })
        assertTrue(list.pricesCoverPhrase)
        assertEquals(252.8, list.amount!!, 1e-9)
    }

    @Test
    fun sentencesSeparateItemsLikeCommas() {
        // Текст GigaAM с телефона: товары разделены точками, как предложения
        val phrase = "Те Национальная гречка фасованная 900 г, — 83—90. Горошек зелёный — 99 ₽. " +
            "Фруктовый сок яблочный, мооя цена — 69,90, Оливки зелёные — 91—90"
        val prices = ShoppingListParser.find(phrase)!!.items.associate { it.name to it.price }
        assertEquals(83.9, prices["Те Национальная гречка фасованная 900 г"])
        assertEquals(99.0, prices["Горошек зелёный"])
        assertEquals(91.9, prices["Оливки зелёные"])
    }

    @Test
    fun decimalPointAndAbbreviationsDoNotSplitItems() {
        val list = ShoppingListParser.find("сок 69.90, хлеб 45 р. и молоко 80")!!
        assertEquals(listOf(69.9, 45.0, 80.0), list.items.map { it.price })
    }

    @Test
    fun lonePriceDoesNotOverwriteKnownPrice() {
        // У предыдущего товара цена уже есть: одиночное число ей не присваивается
        val list = ShoppingListParser.find("рыба 500, 300, хлеб 80")!!
        assertEquals(listOf(500.0, 80.0), list.items.map { it.price })
        assertFalse(list.pricesCoverPhrase)
    }

    @Test
    fun itemsAreFoundBySearch() {
        val note = ShoppingListParser.find("в продуктовом на 2500 купил рыбу, консерву, хлеб")!!.note()
        assertTrue(SearchText.matches("рыба", listOf(note)))
        assertTrue(SearchText.matches("консервы", listOf(note)))
    }
}
