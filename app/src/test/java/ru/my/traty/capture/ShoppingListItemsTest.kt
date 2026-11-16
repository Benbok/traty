package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.EntryParsing
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

/** Товары из фразы становятся позициями траты (а не только текстом заметки). */
class ShoppingListItemsTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(10, 1, "Супермаркет"))),
        CategoryWithSubs(Category(id = 3, name = "Дом и быт", emoji = "🧴"), listOf(Subcategory(30, 3, "Техника"))),
        CategoryWithSubs(Category(id = 4, name = "Прочее", emoji = "📦"), emptyList()),
    )

    private fun clean(entries: List<ParsedEntry>, text: String) = EntryCleanup.apply(entries, text, cats, today)

    @Test
    fun goodsFromAiAreKept() {
        val text = "Эльдорадо 45 тысяч: телефон 40 тысяч, чехол 2 тысячи, клавиатура 3 тысячи"
        val ai = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":45000,"category":"Дом и быт","subcategory":"Техника","place":"Эльдорадо",
            "note":"телефон, чехол, клавиатура","goods":[{"name":"телефон","price":40000},{"name":"чехол","price":2000},{"name":"клавиатура","price":3000}]}]}"""
        )
        val e = clean(ai, text).single()
        assertEquals(45000.0, e.amount, 0.0)
        assertEquals(listOf("телефон", "чехол", "клавиатура"), e.items?.map { it.name })
        assertEquals(listOf(40000.0, 2000.0, 3000.0), e.items?.map { it.price })
        assertTrue(ItemsSanity.problems(listOf(e)).isEmpty())
    }

    @Test
    fun goodsFromPhraseWhenAiGaveNone() {
        val text = "в продуктовом на 2500 купил рыбу 500, консерву, хлеб 80"
        val ai = listOf(ParsedEntry(amount = 2500.0, category = "Продукты", subcategory = "Супермаркет", note = "рыбу, консерву, хлеб"))
        val e = clean(ai, text).single()
        assertEquals(listOf("рыбу", "консерву", "хлеб"), e.items?.map { it.name })
        assertEquals(listOf(500.0, null, 80.0), e.items?.map { it.price })
    }

    @Test
    fun phrasePricesWinWhenAiLostThem() {
        val text = "в продуктовом на 2500 купил рыбу 500, хлеб 80"
        val ai = listOf(
            ParsedEntry(
                amount = 2500.0, category = "Продукты", note = "рыбу, хлеб",
                items = listOf(ru.my.traty.ai.ParsedItem("рыба"), ru.my.traty.ai.ParsedItem("хлеб")),
            )
        )
        assertEquals(listOf(500.0, 80.0), clean(ai, text).single().items?.map { it.price })
    }

    @Test
    fun splitAnswerBecomesOneExpenseWithPricedItems() {
        val text = "в Эльдорадо телефон 40000, чехол 2000"
        val ai = listOf(
            ParsedEntry(amount = 40000.0, category = "Дом и быт", subcategory = "Техника", place = "Эльдорадо", note = "телефон"),
            ParsedEntry(amount = 2000.0, category = "Дом и быт", subcategory = "Техника", place = "Эльдорадо", note = "чехол"),
        )
        val e = clean(ai, text).single()
        assertEquals(42000.0, e.amount, 0.0)
        assertEquals(listOf(40000.0, 2000.0), e.items?.map { it.price })
    }

    @Test
    fun singleThingHasNoItems() {
        val text = "кофе 250"
        val ai = listOf(ParsedEntry(amount = 250.0, category = "Продукты", note = "кофе", items = listOf(ru.my.traty.ai.ParsedItem("кофе", 250.0))))
        assertNull(clean(ai, text).single().items)
    }

    @Test
    fun pricesAboveTotalAreFlagged() {
        val e = ParsedEntry(
            amount = 40000.0, category = "Дом и быт",
            items = listOf(ru.my.traty.ai.ParsedItem("телефон", 40000.0), ru.my.traty.ai.ParsedItem("чехол", 2000.0)),
        )
        // Тысячи разделены неразрывным пробелом
        val p = ItemsSanity.problems(listOf(e)).single().replace(0x00A0.toChar(), ' ')
        assertTrue(p, p.contains("42 000") && p.contains("40 000"))
    }

    @Test
    fun restIsNotAProblem() {
        val e = ParsedEntry(
            amount = 2500.0, category = "Продукты",
            items = listOf(ru.my.traty.ai.ParsedItem("молоко", 90.0), ru.my.traty.ai.ParsedItem("хлеб", 50.0)),
        )
        assertTrue(ItemsSanity.problems(listOf(e)).isEmpty())
    }

    @Test
    fun brokenGoodsDoNotLoseTheExpense() {
        val e = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":300,"category":"Продукты","goods":[{"name":"  ","price":5},{"name":"сыр","price":-3,"qty":0}]}]}"""
        ).single()
        val item = e.items?.single()
        assertEquals("сыр", item?.name)
        assertNull(item?.price)
        assertNull(item?.qty)
    }
}
