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
    fun incomeKeepsDateAndFallsBackToDefaultSource() {
        val r = EntryParsing.decode(
            "{\"items\":[{\"type\":\"income\",\"amount\":80000,\"source\":\"Зарплата\",\"date\":\"2026-11-10\"}," +
                "{\"type\":\"income\",\"amount\":2,\"source\":\"Лотерея\"},{\"type\":\"income\",\"amount\":3,\"source\":\"  \"},{\"type\":\"income\",\"amount\":4}]}"
        )
        assertEquals("2026-11-10", r[0].date)
        assertEquals(listOf("Зарплата", "Прочее", "Прочее", "Прочее"), r.map { it.source })
    }

    @Test
    fun offlineDetectsIncomeByKeyword() {
        assertEquals(EntryType.INCOME, OfflineEntryParser.parse("пришла зарплата 80000", emptyList()).single().entryType)
        assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("кофе 250", emptyList()).single().entryType)
    }

    @Test
    fun severalShopsInOnePhraseGiveSeparateExpensesWithPlace() {
        // Ответ модели на «магазин Пятёрочка 1200, строительный Стройландия 3500, Магнит 800»
        val r = EntryParsing.decode(
            """{"items":[""" +
                """{"type":"expense","amount":1200,"category":"Продукты","place":"Пятёрочка"},""" +
                """{"type":"expense","amount":3500,"category":"Жильё","subcategory":"Ремонт","place":"Стройландия"},""" +
                """{"type":"expense","amount":800,"category":"Продукты","place":"магазин Магнит"}]}"""
        )
        assertEquals(listOf(1200.0, 3500.0, 800.0), r.map { it.amount })
        assertEquals(listOf("Пятёрочка", "Стройландия", "магазин Магнит"), r.map { it.place })
    }

    @Test
    fun promptAsksForOneItemPerShopAndCleanPlace() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("отдельный элемент на каждый магазин" in p)
        assertTrue("«строительный Стройландия» → «Стройландия»" in p)
    }

    @Test
    fun promptExplainsSpokenAmountsWithoutThousand() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("«две сто» → 2100" in p)
        assertTrue("«две пятьсот» → 2500" in p)
        assertTrue("«сто пятьдесят» — это 150" in p)
    }

    @Test
    fun promptMentionsTypesAndToday() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("\"type\"" in p && "income" in p && "2026-11-14" in p)
    }
    @Test
    fun promptMapsMarketplaceNamesToMarketplaceSubcategories() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 15), emptyList())
        assertTrue("категорию «Маркетплейсы» независимо от вида товара" in p)
        assertTrue("Wildberries/WB/ВБ/Вайлдберриз → «Wildberries»" in p)
        assertTrue("Ozon/Озон → «Ozon»" in p)
        assertTrue("Площадку укажи одновременно в subcategory и place" in p)
    }

}
