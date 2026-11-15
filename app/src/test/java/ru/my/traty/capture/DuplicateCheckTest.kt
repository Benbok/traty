package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.EntryType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DuplicateCheckTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 15)
    private fun at(hour: Int, minute: Int = 0, day: LocalDate = today) =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun row(amount: String, place: String = "", note: String = "", category: String = "Продукты", day: LocalDate = today) =
        EntryRow(EntryType.EXPENSE, amount, category, "", place, note, "", day.toString())

    private fun old(id: Long, kopecks: Long, ts: Long, place: String? = null, note: String? = null, category: String = "Продукты") =
        ExistingExpense(id, kopecks, ts, place, note, category)

    @Test
    fun sameShopSameAmountSameDayIsDuplicate() {
        val hint = DuplicateCheck.find(listOf(row("250", place = "Магнит")), listOf(old(7, 25_000, at(14, 5), place = "магнит")), zone, at(14, 20)).single()
        assertEquals(7L, hint?.expenseId)
        assertEquals("Магнит", hint?.label)
        assertTrue(requireNotNull(hint).recent)
    }

    @Test
    fun laterTheSameDayIsStillAskedButNotRecent() {
        val hint = DuplicateCheck.find(listOf(row("250", place = "Магнит")), listOf(old(7, 25_000, at(9), place = "Магнит")), zone, at(18)).single()
        assertFalse(requireNotNull(hint).recent)
    }

    @Test
    fun otherAmountShopOrDayIsNotDuplicate() {
        val existing = listOf(old(7, 25_000, at(14), place = "Магнит"))
        assertNull(DuplicateCheck.find(listOf(row("260", place = "Магнит")), existing, zone, at(15)).single())
        assertNull(DuplicateCheck.find(listOf(row("250", place = "Пятёрочка")), existing, zone, at(15)).single())
        assertNull(DuplicateCheck.find(listOf(row("250", place = "Магнит", day = today.minusDays(1))), existing, zone, at(15)).single())
        assertNull(DuplicateCheck.find(listOf(row("250")), existing, zone, at(15)).single())
    }

    @Test
    fun withoutShopNotesAreCompared() {
        val existing = listOf(old(3, 30_000, at(10), note = "Кофе"))
        assertEquals(3L, DuplicateCheck.find(listOf(row("300", note = "кофе")), existing, zone, at(11)).single()?.expenseId)
        assertNull(DuplicateCheck.find(listOf(row("300", note = "чай")), existing, zone, at(11)).single())
    }

    @Test
    fun withoutShopAndNoteCategoryIsCompared() {
        val existing = listOf(old(4, 120_000, at(10), category = "Связь и подписки"))
        assertEquals(4L, DuplicateCheck.find(listOf(row("1200", category = "связь и подписки")), existing, zone, at(11)).single()?.expenseId)
        assertNull(DuplicateCheck.find(listOf(row("1200", category = "Продукты")), existing, zone, at(11)).single())
    }

    @Test
    fun oneOldExpenseMatchesOnlyOneRow() {
        val rows = listOf(row("250", place = "Магнит"), row("250", place = "Магнит"))
        val hints = DuplicateCheck.find(rows, listOf(old(7, 25_000, at(14), place = "Магнит")), zone, at(15))
        assertEquals(listOf(7L, null), hints.map { it?.expenseId })
    }

    @Test
    fun incomeIsNotChecked() {
        val income = EntryRow(EntryType.INCOME, "250", "", "", "Магнит", "", "Прочее", today.toString())
        assertNull(DuplicateCheck.find(listOf(income), listOf(old(7, 25_000, at(14), place = "Магнит")), zone, at(15)).single())
    }
}
