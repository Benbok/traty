package ru.my.traty.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate

class RecurringScheduleTest {
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun r(
        next: LocalDate,
        title: String = "Яндекс Плюс",
        unit: PeriodUnit = PeriodUnit.MONTH,
        remind: Int = 1,
        state: String = RecurringState.ACTIVE,
    ) = Recurring(
        id = nextId++, title = title, amountKopecks = 39_900, categoryId = 1, periodUnit = unit.key,
        nextEpochDay = next.toEpochDay(), remindDaysBefore = remind, state = state,
    )

    @Test
    fun paidOnTimeMovesToNextPeriod() {
        val item = r(LocalDate.of(2026, 11, 14))
        assertEquals(today, RecurringSchedule.chargeDate(item, today))
        assertEquals(LocalDate.of(2026, 12, 14), RecurringSchedule.nextAfterPaid(item, today))
    }

    @Test
    fun paidEarlyIsDatedTodayAndKeepsTheSchedule() {
        val item = r(LocalDate.of(2026, 11, 20))
        assertEquals(today, RecurringSchedule.chargeDate(item, today))
        assertEquals(LocalDate.of(2026, 12, 20), RecurringSchedule.nextAfterPaid(item, today))
    }

    @Test
    fun overduePaymentIsDatedOnItsDueDateAndJumpsPastToday() {
        val item = r(LocalDate.of(2026, 8, 5))
        assertEquals(LocalDate.of(2026, 8, 5), RecurringSchedule.chargeDate(item, today))
        assertEquals(LocalDate.of(2026, 12, 5), RecurringSchedule.nextAfterPaid(item, today))
    }

    @Test
    fun upcomingListsOnlyActivePaymentsInDateOrder() {
        val items = listOf(
            r(LocalDate.of(2026, 12, 1), "Облако"),
            r(LocalDate.of(2026, 11, 20), "Кино"),
            r(LocalDate.of(2026, 11, 15), "Пауза", state = RecurringState.PAUSED),
            r(LocalDate.of(2027, 3, 1), "Далеко"),
        )
        val u = RecurringSchedule.upcoming(items, today)
        assertEquals(listOf("Кино", "Облако"), u.map { it.item.title })
        assertTrue(u.none { it.overdue })
    }

    @Test
    fun weeklyPaymentRepeatsInsideTheWindow() {
        val u = RecurringSchedule.upcoming(listOf(r(LocalDate.of(2026, 11, 16), unit = PeriodUnit.WEEK)), today)
        assertEquals(
            listOf(16, 23, 30).map { LocalDate.of(2026, 11, it) } + listOf(7, 14).map { LocalDate.of(2026, 12, it) },
            u.map { it.date },
        )
    }

    @Test
    fun overduePaymentShowsOnceFirstAndThenFutureDates() {
        val u = RecurringSchedule.upcoming(listOf(r(LocalDate.of(2026, 10, 25), unit = PeriodUnit.WEEK)), today)
        assertEquals(LocalDate.of(2026, 10, 25), u.first().date)
        assertTrue(u.first().overdue)
        // 1 ноября и 8 ноября остались в прошлом — повторно не показываются
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), u.drop(1).take(1).map { it.date })
        assertEquals(1, u.count { it.overdue })
    }

    @Test
    fun remindersFireInsideTheWindowOnlyOncePerChargeDate() {
        val tomorrow = r(LocalDate.of(2026, 11, 15), remind = 1)
        val inThreeDays = r(LocalDate.of(2026, 11, 17), remind = 1)
        val inThreeWithWindow = r(LocalDate.of(2026, 11, 17), remind = 3)
        val items = listOf(tomorrow, inThreeDays, inThreeWithWindow)

        val due = RecurringSchedule.dueReminders(items, today, emptyMap())
        assertEquals(listOf(tomorrow.id, inThreeWithWindow.id), due.map { it.id })

        // по этой дате уже напоминали — второй раз нет, но после списания (новая дата) снова да
        val notified = mapOf(tomorrow.id to tomorrow.nextEpochDay)
        assertFalse(RecurringSchedule.dueReminders(items, today, notified).any { it.id == tomorrow.id })
        val nextMonth = tomorrow.copy(nextEpochDay = LocalDate.of(2026, 12, 15).toEpochDay())
        assertFalse(RecurringSchedule.dueReminders(listOf(nextMonth), today, notified).any())
        assertTrue(RecurringSchedule.dueReminders(listOf(nextMonth), LocalDate.of(2026, 12, 14), notified).any())
    }

    @Test
    fun noRemindersForPausedOrSilentOrDismissed() {
        val items = listOf(
            r(today, remind = 0),
            r(today, state = RecurringState.PAUSED),
            r(today, state = RecurringState.DISMISSED),
        )
        assertTrue(RecurringSchedule.dueReminders(items, today, emptyMap()).isEmpty())
    }

    @Test
    fun overduePaymentStillGetsOneReminder() {
        val overdue = r(LocalDate.of(2026, 11, 10))
        assertEquals(1, RecurringSchedule.dueReminders(listOf(overdue), today, emptyMap()).size)
    }

    @Test
    fun reminderTitles() {
        assertEquals("Сегодня списание", RecurringSchedule.reminderTitle(0))
        assertEquals("Завтра списание", RecurringSchedule.reminderTitle(1))
        assertEquals("Через 3 дн. списание", RecurringSchedule.reminderTitle(3))
        assertEquals("Списание просрочено на 2 дн.", RecurringSchedule.reminderTitle(-2))
    }
}
