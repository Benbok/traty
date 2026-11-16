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
    fun amountWithKopecksHasNoFloatingPointTail() {
        // Склейка трат складывает дробные суммы: 83,9 + 99 + 69,9 + 91,9 = 344,70000000000005 в Double
        val row = EntryRow.fromParsed(ParsedEntry(amount = 83.9 + 99.0 + 69.9 + 91.9), cats, day)
        assertEquals("344.7", row.amountText)
        assertEquals(34_470L, row.kopecks())
        assertEquals("0.1", EntryRow.fromParsed(ParsedEntry(amount = 0.1 + 0.2 - 0.2), cats, day).amountText)
    }

    @Test
    fun rowFromParsedCleansShopName() {
        val row = EntryRow.fromParsed(ParsedEntry(amount = 800.0, category = "Продукты", place = "магазин «Магнит»"), cats, day)
        assertEquals("Магнит", row.place)
    }

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
    fun kopecksParsesCommaAndSpaceVariants() {
        val nbsp = 0x00A0.toChar()
        val nnbsp = 0x202F.toChar()
        val base = EntryRow.empty()
        assertEquals(8_000_050L, base.copy(amountText = "80 000,50").kopecks())
        assertEquals(8_000_050L, base.copy(amountText = "80${nbsp}000,50").kopecks())
        assertEquals(8_000_050L, base.copy(amountText = "80${nnbsp}000,50").kopecks())
        assertEquals(150L, base.copy(amountText = "1,5").kopecks())
        assertEquals(3_000_000L, base.copy(amountText = "30000").kopecks())
        assertNull(base.copy(amountText = "-5").kopecks())
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
