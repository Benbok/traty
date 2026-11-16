package ru.my.traty.data.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemNamesTest {
    @Test
    fun caseFormsAreOneProduct() {
        assertEquals(ItemNames.key("рыба"), ItemNames.key("рыбу"))
        assertEquals(ItemNames.key("Рыба"), ItemNames.key("рыбы"))
        assertEquals(ItemNames.key("чехол для телефона"), ItemNames.key("Чехол для телефонов"))
        assertEquals(ItemNames.key("Молоко"), ItemNames.key("молока"))
    }

    @Test
    fun yoAndPunctuationDoNotMatter() {
        assertEquals(ItemNames.key("Тёрка"), ItemNames.key("терка"))
        assertEquals(ItemNames.key("клавиатура, беспроводная"), ItemNames.key("Клавиатура беспроводная"))
    }

    @Test
    fun differentProductsStayApart() {
        assertNotEquals(ItemNames.key("телефон"), ItemNames.key("чехол"))
        assertNotEquals(ItemNames.key("Samsung A55"), ItemNames.key("Samsung A35"))
    }

    @Test
    fun emptyNameHasNoKey() {
        assertEquals("", ItemNames.key("  , . "))
        assertNull(ItemNames.normalize(" , "))
    }

    @Test
    fun normalizeCapitalizesAndKeepsLongNames() {
        assertEquals("Телефон", ItemNames.normalize("  телефон "))
        val long = "смартфон Samsung Galaxy A55 8/256 ГБ чёрный с гарантией"
        assertEquals(long.replaceFirstChar { it.uppercaseChar() }, ItemNames.normalize(long))
    }
}
