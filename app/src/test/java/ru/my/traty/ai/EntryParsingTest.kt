package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryParsingTest {
    @Test
    fun decodesMixedTypes() {
        val r = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":250,"category":"Кафе и рестораны"},{"type":"income","amount":80000,"source":"Зарплата"}]}"""
        )
        assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), r.map { it.entryType })
        assertEquals("Зарплата", r[1].source)
    }

    @Test
    fun unknownTypeHasNullEntryType() {
        val r = EntryParsing.decode("""[{"type":"goal_deposit","amount":5000}]""")
        assertNull(r.single().entryType)
    }

    @Test
    fun incomeSourceIsNormalized() {
        val r = EntryParsing.decode("""{"items":[{"type":"income","amount":100,"source":"зарплата"}]}""")
        assertEquals("Зарплата", r.single().source)
    }

    @Test
    fun dropsNonPositiveAndStripsFence() {
        val r = EntryParsing.decode("```json\n{\"items\":[{\"amount\":0},{\"amount\":400,\"category\":\"Транспорт\"}]}\n```")
        assertEquals(listOf(400.0), r.map { it.amount })
        assertEquals(EntryType.EXPENSE, r.single().entryType)
    }

    @Test
    fun offlineDetectsIncomeByKeyword() {
        assertEquals(EntryType.INCOME, OfflineEntryParser.parse("пришла зарплата 80000", emptyList()).single().entryType)
        assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("кофе 250", emptyList()).single().entryType)
    }

    @Test
    fun promptMentionsTypesAndToday() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("\"type\"" in p && "income" in p && "2026-11-14" in p)
    }
}
