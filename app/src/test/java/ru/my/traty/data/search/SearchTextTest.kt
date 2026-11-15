package ru.my.traty.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    private val catNote = listOf("Питомцы", null, "Зоомагазин", "лекарство для кошки Чуни от блох")

    @Test
    fun `словоформы одного слова дают одну основу`() {
        val stem = RussianStemmer::stem
        assertEquals("кошк", stem("кошке"))
        assertEquals("кошк", stem("кошки"))
        assertEquals("кошк", stem("кошка"))
        assertEquals("кошк", stem("кошкой"))
        assertEquals(stem("лекарство"), stem("лекарства"))
        assertEquals(stem("лекарство"), stem("лекарствами"))
        assertEquals(stem("корм"), stem("корма"))
        assertEquals(stem("продукты"), stem("продуктов"))
        assertEquals("ikea", stem("ikea"))
    }

    @Test
    fun `лекарство кошке находит заметку про кошку`() {
        assertTrue(SearchText.matches("лекарство кошке", catNote))
        assertTrue(SearchText.matches("кошке лекарство", catNote))
        assertTrue(SearchText.matches("лекарства для кошки", catNote))
        assertTrue(SearchText.matches("Лекарство от блох", catNote))
        assertTrue(SearchText.matches("чуне", catNote))
    }

    @Test
    fun `все значимые слова запроса должны найтись`() {
        assertFalse(SearchText.matches("лекарство собаке", catNote))
        assertFalse(SearchText.matches("корм кошке", catNote))
    }

    @Test
    fun `слова ищутся во всех полях записи сразу`() {
        assertTrue(SearchText.matches("питомцы блохи", catNote))
        assertTrue(SearchText.matches("зоомагазин кошка", catNote))
    }

    @Test
    fun `часть слова при наборе тоже находит`() {
        assertTrue(SearchText.matches("лекар", catNote))
        assertTrue(SearchText.matches("зоо", catNote))
        assertTrue(SearchText.matches("магазин", catNote))
    }

    @Test
    fun `регистр и ё не важны`() {
        assertTrue(SearchText.matches("ПЯТЁРОЧКА", listOf("Пятерочка")))
        assertTrue(SearchText.matches("пятерочке", listOf("Пятёрочка")))
    }

    @Test
    fun `предлоги не мешают, а одни предлоги не показывают всё`() {
        assertEquals(listOf("кофе"), SearchText.queryWords("кофе с собой").take(1))
        assertTrue(SearchText.matches("с", listOf("Кофе с собой")))
        assertFalse(SearchText.matches("с", listOf("Такси")))
        assertTrue(SearchText.matches("   ", listOf("Такси")))
    }

    @Test
    fun `короткая основа не даёт ложных совпадений`() {
        assertFalse(SearchText.matches("кот", listOf("Кино")))
        assertFalse(SearchText.matches("кошке", listOf("Кофе")))
    }
}
