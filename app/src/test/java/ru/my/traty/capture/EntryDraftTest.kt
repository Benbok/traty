package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import java.time.LocalDate

class EntryDraftTest {
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Кафе и рестораны", emoji = "☕"), emptyList()),
    )
    private val day = LocalDate.of(2026, 11, 13)

    @Test
    fun rowFromParsedExpenseUsesKnownCategory() {
        val row = EntryRow.fromParsed(ParsedEntry(amount = 250.0, category = "кафе и рестораны", place = "Старбакс"), cats, day)
        assertEquals(EntryType.EXPENSE, row.type)
        assertEquals("Кафе и рестораны", row.category)
        assertEquals("250", row.amountText)
        assertEquals("Старбакс", row.place)
        assertNull(row.suggestedCategory)
    }

    @Test
    fun rowFromUnknownCategorySuggestsNew() {
        val row = EntryRow.fromParsed(ParsedEntry(amount = 99.5, category = "питомцы", emoji = "🐶"), cats, day)
        assertEquals("Питомцы", row.category)
        assertEquals("99.5", row.amountText)
        assertEquals(NewCategory("Питомцы", "🐶"), row.suggestedCategory)
    }

    @Test
    fun rowDefaultDateIsSpokenAtDay() {
        val row = EntryRow.fromParsed(ParsedEntry(amount = 10.0), cats, day)
        assertEquals("2026-11-13", row.dateText)
        val explicit = EntryRow.fromParsed(ParsedEntry(amount = 10.0, date = "2026-11-01"), cats, day)
        assertEquals("2026-11-01", explicit.dateText)
    }

    @Test
    fun incomeRowKeepsNormalizedSource() {
        val row = EntryRow.fromParsed(ParsedEntry(type = "income", amount = 80000.0, source = "зарплата"), cats, day)
        assertEquals(EntryType.INCOME, row.type)
        assertEquals("Зарплата", row.source)
        assertEquals(8_000_000L, row.kopecks())
    }

    @Test
    fun invalidAmountHasNoKopecks() {
        val base = EntryRow.fromParsed(ParsedEntry(amount = 1.0), cats, day)
        assertNull(base.copy(amountText = "").kopecks())
        assertNull(base.copy(amountText = "abc").kopecks())
        assertNull(base.copy(amountText = "0").kopecks())
        assertEquals(125_050L, base.copy(amountText = "1 250,5").kopecks())
    }
}
