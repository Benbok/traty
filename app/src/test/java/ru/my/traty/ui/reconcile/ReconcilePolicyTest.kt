package ru.my.traty.ui.reconcile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReconcilePolicyTest {
    private val nov15 = LocalDate.of(2026, 11, 15)

    @Test
    fun noPromptWhenRecordsOnlyInCurrentPeriod() {
        assertFalse(ReconcilePolicy.shouldPrompt(nov15, 1, LocalDate.of(2026, 11, 2), null))
    }

    @Test
    fun promptOnFirstDayOfNextPeriod() {
        assertFalse(ReconcilePolicy.shouldPrompt(LocalDate.of(2026, 11, 30), 1, LocalDate.of(2026, 11, 2), null))
        assertTrue(ReconcilePolicy.shouldPrompt(LocalDate.of(2026, 12, 1), 1, LocalDate.of(2026, 11, 2), null))
    }

    @Test
    fun snoozeUntilTomorrowHidesCard() {
        assertFalse(ReconcilePolicy.shouldPrompt(nov15, 1, LocalDate.of(2026, 10, 20), nov15.plusDays(1)))
    }

    @Test
    fun snoozeUntilYesterdayShowsCard() {
        assertTrue(ReconcilePolicy.shouldPrompt(nov15, 1, LocalDate.of(2026, 10, 20), nov15.minusDays(1)))
    }

    @Test
    fun snoozedOnDayDHidesOnDAndShowsOnDPlusOne() {
        // «Напомнить завтра»: в настройки сохраняется сегодняшняя дата.
        val d = nov15
        val ref = LocalDate.of(2026, 10, 20)
        assertFalse(ReconcilePolicy.shouldPrompt(d, 1, ref, d))
        assertTrue(ReconcilePolicy.shouldPrompt(d.plusDays(1), 1, ref, d))
    }

    @Test
    fun noPromptWithoutReference() {
        assertFalse(ReconcilePolicy.shouldPrompt(nov15, 1, null, null))
    }

    @Test
    fun customStartDayPromptsOnlyFromPeriodStart() {
        // Сверка 12 дек, период 10 дек - 9 янв
        val ref = LocalDate.of(2026, 12, 12)
        assertFalse(ReconcilePolicy.shouldPrompt(LocalDate.of(2027, 1, 9), 10, ref, null))
        assertTrue(ReconcilePolicy.shouldPrompt(LocalDate.of(2027, 1, 10), 10, ref, null))
    }

    @Test
    fun entryDateIsLastDayOfPreviousPeriodWhenReferenceIsOlder() {
        // Сверка 12 дек (период 10 дек - 9 янв), reference в предыдущем периоде -> 9 дек
        val today = LocalDate.of(2026, 12, 12)
        assertEquals(LocalDate.of(2026, 12, 9), ReconcilePolicy.entryDate(today, 10, LocalDate.of(2026, 11, 20), null))
    }

    @Test
    fun redoEntryDateUsesReconciliationBeforeTheReplacedOne() {
        // startDay=10: предыдущая сверка 20 ноя, сегодня 12 дек -> 9 дек (а не сегодня)
        val today = LocalDate.of(2026, 12, 12)
        assertEquals(LocalDate.of(2026, 12, 9), ReconcilePolicy.entryDate(today, 10, LocalDate.of(2026, 11, 20), null))
        // если опираться на заменяемую сегодняшнюю сверку - получилось бы сегодня
        assertEquals(today, ReconcilePolicy.entryDate(today, 10, today, null))
    }

    @Test
    fun entryDateIsTodayWithinSamePeriod() {
        val today = LocalDate.of(2026, 12, 12)
        assertEquals(today, ReconcilePolicy.entryDate(today, 10, LocalDate.of(2026, 12, 11), null))
    }

    @Test
    fun parseKopecksHandlesSeparatorsAndGarbage() {
        val nbsp = 0x00A0.toChar()
        assertEquals(2_550_000L, ReconcilePolicy.parseKopecks("25${nbsp}500"))
        assertEquals(1_234_50L, ReconcilePolicy.parseKopecks("1 234,5"))
        assertEquals(100L, ReconcilePolicy.parseKopecks("1.00"))
        assertEquals(null, ReconcilePolicy.parseKopecks(""))
        assertEquals(null, ReconcilePolicy.parseKopecks("abc"))
    }

    @Test
    fun entryDateFallsBackToFirstRecordDateAndToday() {
        val today = LocalDate.of(2026, 12, 12)
        assertEquals(LocalDate.of(2026, 12, 9), ReconcilePolicy.entryDate(today, 10, null, LocalDate.of(2026, 11, 20)))
        assertEquals(today, ReconcilePolicy.entryDate(today, 10, null, null))
    }
}
