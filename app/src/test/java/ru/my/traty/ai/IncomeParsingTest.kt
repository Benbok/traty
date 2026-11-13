package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeParsingTest {
    @Test
    fun decodeObjectInsideCodeFence() {
        val r = IncomeParsing.decode(
            "```json\n{\"items\":[{\"amount\":80000,\"source\":\"Зарплата\",\"date\":\"2026-11-10\"},{\"amount\":500,\"source\":\"Перевод\"}]}\n```"
        )
        assertEquals(listOf(80000.0, 500.0), r.map { it.amount })
        assertEquals("Зарплата", r[0].source)
        assertEquals("2026-11-10", r[0].date)
        assertEquals("Перевод", r[1].source)
    }

    @Test
    fun decodeDropsNonPositive() {
        val r = IncomeParsing.decode("[{\"amount\":0,\"source\":\"Аванс\"},{\"amount\":-5},{\"amount\":99.5,\"source\":\"Аванс\"}]")
        assertEquals(1, r.size)
        assertEquals(99.5, r[0].amount, 0.0)
    }

    @Test
    fun unknownSourceFallsBackToDefault() {
        val r = IncomeParsing.decode(
            "{\"items\":[{\"amount\":1,\"source\":\"зАРПЛАТА\"},{\"amount\":2,\"source\":\"Лотерея\"},{\"amount\":3,\"source\":\"  \"},{\"amount\":4}]}"
        )
        assertEquals(listOf("Зарплата", "Прочее", "Прочее", "Прочее"), r.map { it.source })
    }

    @Test
    fun offlineSalary() {
        val r = OfflineIncomeParser.parse("зарплата 80000")
        assertEquals(1, r.size)
        assertEquals(80000.0, r[0].amount, 0.0)
        assertEquals("Зарплата", r[0].source)
    }

    @Test
    fun offlineThousandsWithSpace() {
        val r = OfflineIncomeParser.parse("аванс 30 000")
        assertEquals(30000.0, r[0].amount, 0.0)
        assertEquals("Аванс", r[0].source)
        assertEquals(30000.5, OfflineIncomeParser.parse("аванс 30 000,50")[0].amount, 0.0)
        assertEquals(30000.0, OfflineIncomeParser.parse("аванс 30 000 руб")[0].amount, 0.0)
    }

    @Test
    fun offlineFirstNumberOnly() {
        // Только первое число: «30 000» — одна сумма, а « 500» после «и» в сумму не входит.
        val r = OfflineIncomeParser.parse("аванс 30 000 и 500")
        assertEquals(1, r.size)
        assertEquals(30000.0, r[0].amount, 0.0)
        // Два числа без группировки по 3 цифры — не склеиваются.
        assertEquals(30.0, OfflineIncomeParser.parse("аванс 30 50")[0].amount, 0.0)
    }

    @Test
    fun offlineKeywordVariants() {
        val a = OfflineIncomeParser.parse("вернули 1500")[0]
        assertEquals("Возврат", a.source)
        assertEquals(1500.0, a.amount, 0.0)
        assertEquals("Дежурства", OfflineIncomeParser.parse("дежурство 4500")[0].source)
        assertEquals("Подработка", OfflineIncomeParser.parse("подработка 2000")[0].source)
        assertEquals("Прочее", OfflineIncomeParser.parse("нашёл 100")[0].source)
    }

    @Test
    fun offlineNoteIsRemainder() {
        assertEquals("зарплата", OfflineIncomeParser.parse("зарплата 80000 руб")[0].note)
        assertNull(OfflineIncomeParser.parse("100")[0].note)
    }

    @Test
    fun offlineNoNumberReturnsEmpty() {
        assertTrue(OfflineIncomeParser.parse("зарплата").isEmpty())
    }
}
