package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    @Test
    fun normalizeStripsGenericWordsAndQuotes() {
        assertEquals("Пятёрочка", Places.normalize("магазин Пятёрочка"))
        assertEquals("Стройландия", Places.normalize("строительный магазин «Стройландия»"))
        assertEquals("Магнит", Places.normalize("  супермаркет   \"магнит\" "))
        assertNull(Places.normalize("   "))
        assertNull(Places.normalize(null))
    }

    @Test
    fun adjectiveWithoutShopWordIsPartOfName() {
        assertEquals("Строительный двор", Places.normalize("строительный двор"))
        assertEquals("Магазин", Places.normalize("магазин"))
    }

    @Test
    fun keyIgnoresCaseYoAndQuotes() {
        val k = Places.key("Пятёрочка")
        assertEquals(k, Places.key("пятерочка"))
        assertEquals(k, Places.key("магазин \"Пятерочка\""))
        assertEquals("", Places.key(null))
    }

    @Test
    fun canonicalReusesKnownSpelling() {
        assertEquals("Пятёрочка", Places.canonical("магазин пятерочка", listOf("Магнит", "Пятёрочка")))
        assertEquals("Лента", Places.canonical("лента", listOf("Магнит")))
        assertNull(Places.canonical("", listOf("Магнит")))
    }

    @Test
    fun groupMergesSpellingsAndPutsNoPlaceLast() {
        val rows = listOf(
            PlaceTotal(1, "Пятёрочка", 120_000, 3),
            PlaceTotal(1, "пятерочка", 30_000, 1),
            PlaceTotal(1, null, 500_000, 2),
            PlaceTotal(1, "Магнит", 80_000, 1),
            PlaceTotal(2, "Пятёрочка", 10_000, 1),
        )
        val g = Places.group(rows)
        assertEquals(listOf("Пятёрочка", "Магнит", Places.NO_PLACE), g.map { it.name })
        assertEquals(160_000, g[0].totalKopecks)
        assertEquals(5, g[0].count)
        assertEquals(listOf(1L, 2L), g[0].categoryIds)
        assertTrue(g.last().isNoPlace)
    }

    @Test
    fun byCategorySplitsPerCategory() {
        val rows = listOf(
            PlaceTotal(1, "Пятёрочка", 100, 1),
            PlaceTotal(2, "Стройландия", 300, 1),
            PlaceTotal(2, null, 50, 1),
        )
        val m = Places.byCategory(rows)
        assertEquals(listOf("Пятёрочка"), m[1L]!!.map { it.name })
        assertEquals(listOf("Стройландия", Places.NO_PLACE), m[2L]!!.map { it.name })
        assertTrue(Places.hasNamed(m[2L]))
        assertFalse(Places.hasNamed(listOf(StoreTotal("", Places.NO_PLACE, 1, 1))))
    }
}
