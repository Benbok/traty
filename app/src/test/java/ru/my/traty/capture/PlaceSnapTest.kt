package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.ai.ParsedEntry

class PlaceSnapTest {
    private val known = listOf("Пятёрочка", "Магнит", "Леруа Мерлен")

    @Test fun oneLetterOffTakesKnownSpelling() {
        assertEquals("Пятёрочка", PlaceSnap.snap("Пятёрочко", known))
        assertEquals("Магнит", PlaceSnap.snap("Магнид", known))
    }

    @Test fun twoLettersOnlyForLongNames() {
        assertEquals("Леруа Мерлен", PlaceSnap.snap("Леруа Мирлин", known))
        assertNull(PlaceSnap.snap("Магнатты", known))
    }

    @Test fun knownShortOrDifferentNamesStay() {
        assertNull(PlaceSnap.snap("Магнит", known))
        assertNull(PlaceSnap.snap("Лента", known))
        assertNull(PlaceSnap.snap("Перекрёсток", known))
        assertNull(PlaceSnap.snap(null, known))
    }

    @Test fun ambiguousNearNamesAreNotSnapped() {
        assertNull(PlaceSnap.snap("Магнут", listOf("Магнит", "Магнум")))
    }

    @Test fun appliesToEntries() {
        val r = PlaceSnap.apply(listOf(ParsedEntry(amount = 100.0, category = "Продукты", place = "Магнид")), known).single()
        assertEquals("Магнит", r.place)
    }
}
