package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import java.time.LocalDate

class DateCheckTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun e(date: String?) = ParsedEntry(type = "expense", amount = 100.0, date = date)
    private fun run(phrase: String, date: String?) = DateCheck.apply(listOf(e(date)), phrase, today)

    @Test
    fun sensibleDatesStay() {
        assertEquals("2026-11-15", run("вчера кофе 250", "2026-11-15").entries.single().date)
        assertEquals("2026-11-16", run("кофе 250", "2026-11-16").entries.single().date)
        assertEquals("2026-09-20", run("кофе 250", "2026-09-20").entries.single().date)
        assertTrue(run("кофе 250", "2026-11-15").problems.isEmpty())
        assertNull(run("кофе 250", null).entries.single().date)
    }

    @Test
    fun futureDateIsReplacedByToday() {
        val r = run("кофе 250", "2026-11-20")
        assertNull(r.entries.single().date)
        assertEquals(1, r.problems.size)
        assertTrue(r.problems.single(), "20.11.2026" in r.problems.single())
    }

    @Test
    fun oldDateWithoutWordsInPhraseIsReplaced() {
        // Модель поставила прошлый год, а во фразе даты нет
        val r = run("кофе 250", "2025-11-16")
        assertNull(r.entries.single().date)
        assertEquals(1, r.problems.size)
    }

    @Test
    fun oldDateIsKeptWhenPhraseNamesIt() {
        assertEquals("2026-03-10", run("10 марта кофе 250", "2026-03-10").entries.single().date)
        assertEquals("2026-03-10", run("кофе 250 восемь месяцев назад", "2026-03-10").entries.single().date)
        assertEquals("2026-03-10", run("кофе 250 в прошлом году", "2026-03-10").entries.single().date)
        assertTrue(run("кофе 250 10.03", "2026-03-10").problems.isEmpty())
    }

    @Test
    fun garbageDateIsReplaced() {
        val r = run("кофе 250", "вчера")
        assertNull(r.entries.single().date)
        assertEquals(1, r.problems.size)
    }

    @Test
    fun futureDateIsWrongEvenIfPhraseNamesADate() {
        assertNull(run("5 декабря кофе 250", "2026-12-05").entries.single().date)
    }
}
