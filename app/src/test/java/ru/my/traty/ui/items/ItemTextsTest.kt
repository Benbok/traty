package ru.my.traty.ui.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.items.ItemAmounts
import ru.my.traty.data.items.ProductTotal

class ItemTextsTest {
    private fun product(purchases: Int, places: List<String>, mlMilli: Long = 0, unpriced: Int = 0) =
        ProductTotal("k", "Молоко", 30_000, purchases, unpriced, ItemAmounts(piecesMilli = purchases * 1000L, mlMilli = mlMilli, assumedPieces = purchases), places, 0)

    @Test
    fun quantityWithComma() {
        assertEquals("1,5", ItemTexts.quantity(1_500))
        assertEquals("2", ItemTexts.quantity(2_000))
        assertEquals("4 л", ItemTexts.quantity(4_000, "л"))
        assertNull(ItemTexts.quantity(null, "л"))
    }

    @Test
    fun subtitleShowsPurchasesQuantityAndPlaces() {
        assertEquals("3 покупки · 3 шт · 4 л · Пятёрочка, Магнит", ItemTexts.subtitle(product(3, listOf("Пятёрочка", "Магнит"), 4_000_000)))
        assertEquals("1 покупка · Эльдорадо", ItemTexts.subtitle(product(1, listOf("Эльдорадо"))))
        assertEquals(
            "5 покупок · без цены: 1 · Пятёрочка, Магнит и ещё 1",
            ItemTexts.subtitle(product(5, listOf("Пятёрочка", "Магнит", "Лента"), unpriced = 1)),
        )
        assertEquals("2 покупки", ItemTexts.subtitle(product(2, listOf("Пятёрочка")), showPlaces = false))
    }
}
