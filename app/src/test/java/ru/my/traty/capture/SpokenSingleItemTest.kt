package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ParsedItem
import ru.my.traty.ai.ShoppingListParser
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

/**
 * Один товар, который распознавание речи разбило запятыми и заминками:
 * «Говядина, гуляш, а-а, пол 500 г, аа 457 ₽» — одна трата на 457 ₽ без позиций.
 */
class SpokenSingleItemTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(10, 1, "Супермаркет"))),
        CategoryWithSubs(Category(id = 4, name = "Прочее", emoji = "📦"), emptyList()),
    )

    private fun clean(entries: List<ParsedEntry>, text: String) = EntryCleanup.apply(entries, text, cats, today)

    private val phrase = "Говядина, гуляш, а-а, пол 500 г, аа 457 ₽"

    @Test
    fun oneSumIsOneExpenseWithoutItems() {
        val ai = listOf(ParsedEntry(amount = 457.0, category = "Продукты", subcategory = "Супермаркет", note = "говядина гуляш 500 г"))
        val e = clean(ai, phrase).single()
        assertEquals(457.0, e.amount, 0.0)
        assertEquals("говядина гуляш 500 г", e.note)
        assertNull(e.items)
    }

    @Test
    fun interjectionsAreNotGoods() {
        val names = ShoppingListParser.find(phrase)?.items?.map { it.name }.orEmpty()
        assertFalse(names.toString(), names.any { ShoppingListParser.isInterjection(it) })
        assertFalse(names.toString(), names.any { it.equals("а-а", true) || it.equals("аа", true) })
    }

    @Test
    fun priceAfterInterjectionStaysWithPreviousGood() {
        val items = ShoppingListParser.find(phrase)?.items.orEmpty()
        assertEquals(457.0, items.last().price ?: 0.0, 0.0)
        assertEquals(1, items.count { it.price != null })
    }

    @Test
    fun interjectionGoodsFromAiAreDropped() {
        val ai = listOf(
            ParsedEntry(
                amount = 457.0, category = "Продукты", note = "говядина гуляш",
                items = listOf(ParsedItem("говядина гуляш", 457.0), ParsedItem("а-а")),
            )
        )
        assertNull(clean(ai, "говядина гуляш а-а 457").single().items)
    }

    @Test
    fun interjectionWords() {
        listOf("а-а", "аа", "Ааа", "э-э", "ммм", "хм", "э").forEach { assertTrue(it, ShoppingListParser.isInterjection(it)) }
        listOf("рыба", "мама", "яйца", "чай", "пол", "500").forEach { assertFalse(it, ShoppingListParser.isInterjection(it)) }
    }

    @Test
    fun realListWithTwoPricesStillHasItems() {
        val text = "в Пятёрочке хлеб 80, э-э, молоко 90"
        val ai = listOf(ParsedEntry(amount = 170.0, category = "Продукты", place = "Пятёрочка", note = "хлеб, молоко"))
        val e = clean(ai, text).single()
        assertEquals(listOf("хлеб", "молоко"), e.items?.map { it.name })
        assertEquals(listOf(80.0, 90.0), e.items?.map { it.price })
    }

    @Test
    fun listWithNamedTotalKeepsItemsWithoutPrices() {
        val text = "в продуктовом на 2500 купил рыбу, консерву, хлеб"
        val ai = listOf(ParsedEntry(amount = 2500.0, category = "Продукты", note = "рыбу, консерву, хлеб"))
        assertEquals(listOf("рыбу", "консерву", "хлеб"), clean(ai, text).single().items?.map { it.name })
    }

    @Test
    fun listWithOnlyOnePriceAndNoTotalHasNoItems() {
        val text = "сыр, колбаса 600"
        val ai = listOf(ParsedEntry(amount = 600.0, category = "Продукты", note = "сыр, колбаса"))
        assertNull(clean(ai, text).single().items)
    }
}
