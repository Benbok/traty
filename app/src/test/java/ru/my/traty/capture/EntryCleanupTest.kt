package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.ai.EntryParsing
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

class EntryCleanupTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val phrase = "зоомагазин вчера 731 лекарство для кошки Чуни от блох"
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(10, 1, "Супермаркет"))),
        CategoryWithSubs(
            Category(id = 3, name = "Питомцы", emoji = "🐾"),
            listOf(Subcategory(30, 3, "Корм"), Subcategory(31, 3, "Ветеринар")),
        ),
        CategoryWithSubs(Category(id = 4, name = "Прочее", emoji = "📦"), emptyList()),
    )

    private fun clean(entry: ParsedEntry, text: String = phrase) = EntryCleanup.apply(listOf(entry), text, cats, today).single()

    @Test
    fun goodAnswerStaysAsIs() {
        val ai = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":731,"category":"Питомцы","subcategory":"Зоомагазин","place":null,"note":"лекарство для кошки Чуни от блох","date":"2026-11-14"}]}"""
        ).single()
        val e = clean(ai)
        assertEquals(ai, e)
        val row = EntryRow.fromParsed(e, cats, today)
        assertEquals("Питомцы", row.category)
        assertEquals("Зоомагазин", row.subcategory)
        assertEquals("лекарство для кошки Чуни от блох", row.note)
        assertEquals("2026-11-14", row.dateText)
        assertEquals("", row.place)
        assertNull(row.suggestedCategory)
    }

    @Test
    fun shopTypeInPlaceBecomesSubcategory() {
        val e = clean(ParsedEntry(amount = 731.0, category = "Прочее", place = "Зоомагазин", note = "лекарство для кошки Чуни от блох"))
        assertNull(e.place)
        assertEquals("Питомцы", e.category)
        assertEquals("Зоомагазин", e.subcategory)
    }

    @Test
    fun placeIsNotGluedToNote() {
        val e = clean(ParsedEntry(amount = 731.0, category = "Питомцы", subcategory = "Зоомагазин", note = "Зоомагазин, лекарство для кошки Чуни от блох"))
        assertEquals("лекарство для кошки Чуни от блох", e.note)
    }

    @Test
    fun lostNoteIsTakenFromPhrase() {
        val e = clean(ParsedEntry(amount = 731.0, category = "Питомцы", subcategory = "Зоомагазин"))
        assertEquals("лекарство для кошки Чуни от блох", e.note)
    }

    @Test
    fun lostNoteDoesNotRepeatShopName() {
        val e = clean(
            ParsedEntry(amount = 731.0, category = "Питомцы", place = "Четыре лапы"),
            text = "Четыре лапы вчера 731 лекарство для кошки Чуни от блох",
        )
        assertEquals("Четыре лапы", e.place)
        assertEquals("лекарство для кошки Чуни от блох", e.note)
    }

    @Test
    fun yesterdayFromPhraseWinsOverModel() {
        val e = clean(ParsedEntry(amount = 731.0, category = "Питомцы", note = "лекарство", date = "2026-11-15"))
        assertEquals("2026-11-14", e.date)
    }

    @Test
    fun subcategoryEqualToCategoryIsDropped() {
        assertNull(clean(ParsedEntry(amount = 731.0, category = "Питомцы", subcategory = "питомцы", note = "x")).subcategory)
    }

    @Test
    fun knownSubcategoryPullsItsCategoryInsteadOfOther() {
        val e = clean(ParsedEntry(amount = 300.0, category = "Прочее", subcategory = "корм", note = "корм"), text = "корм 300")
        assertEquals("Питомцы", e.category)
        assertEquals("Корм", e.subcategory)
    }

    @Test
    fun newCategoryAndSubcategoryAreSuggested() {
        val e = clean(
            ParsedEntry(amount = 731.0, category = "Аквариум", emoji = "🐠", subcategory = "Корм для рыб", note = "корм для рыбок"),
            text = "корм для рыбок 731",
        )
        val row = EntryRow.fromParsed(e, cats, today)
        assertEquals("Аквариум", row.category)
        assertEquals(NewCategory("Аквариум", "🐠"), row.suggestedCategory)
        assertEquals("Корм для рыб", row.subcategory)
    }

    @Test
    fun severalItemsKeepTheirOwnNotesAndDates() {
        val items = listOf(
            ParsedEntry(amount = 731.0, category = "Питомцы", subcategory = "Зоомагазин", note = "лекарство", date = "2026-11-14"),
            ParsedEntry(amount = 500.0, category = "Продукты", place = "Пятёрочка"),
        )
        val r = EntryCleanup.apply(items, "зоомагазин вчера 731 лекарство и Пятёрочка 500", cats, today)
        assertEquals(listOf("лекарство", null), r.map { it.note })
        assertEquals(listOf("2026-11-14", null), r.map { it.date })
    }
}
