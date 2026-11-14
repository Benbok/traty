package ru.my.traty.data.recurring

import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate

/** Ожидаемое списание: [overdue] — дата уже прошла, а «списалось» не отмечено. */
data class RecurringCharge(val date: LocalDate, val item: Recurring, val overdue: Boolean)

/** Расписание регулярных платежей: отметка «списалось», календарь вперёд, кому пора напомнить. Чистый код. */
object RecurringSchedule {
    const val CALENDAR_DAYS = 30

    /** Дата траты при отметке «списалось»: плановая, если она уже наступила, иначе сегодня (заплатили раньше срока). */
    fun chargeDate(item: Recurring, today: LocalDate): LocalDate = minOf(item.nextDate(), today)

    /** Следующее списание после отметки: на период вперёд от плановой даты и строго после [today]. */
    fun nextAfterPaid(item: Recurring, today: LocalDate): LocalDate {
        val period = item.period()
        var d = period.advance(item.nextDate())
        while (!d.isAfter(today)) d = period.advance(d)
        return d
    }

    /**
     * Списания действующих платежей от [today] на [days] дней вперёд, по датам.
     * Просроченное списание показывается один раз, на своей дате, и идёт первым.
     */
    fun upcoming(items: List<Recurring>, today: LocalDate, days: Int = CALENDAR_DAYS): List<RecurringCharge> {
        val end = today.plusDays(days.toLong())
        val result = mutableListOf<RecurringCharge>()
        for (item in items.filter { it.state == RecurringState.ACTIVE }) {
            val period = item.period()
            var d = item.nextDate()
            var first = true
            var guard = 0
            while (!d.isAfter(end) && guard++ < 400) {
                if (first) {
                    result += RecurringCharge(d, item, overdue = d.isBefore(today))
                    first = false
                } else if (!d.isBefore(today)) {
                    result += RecurringCharge(d, item, overdue = false)
                }
                d = period.advance(d)
            }
        }
        return result.sortedWith(compareBy({ it.date }, { it.item.title }))
    }

    /**
     * Платежи, о которых пора напомнить сегодня: напоминания идут каждый день, начиная за
     * [Recurring.remindDaysBefore] дней до списания и до самого дня списания включительно.
     * [notified]: id платежа → последний день, когда о нём напомнили (в одни сутки не больше одного уведомления).
     */
    fun dueReminders(items: List<Recurring>, today: LocalDate, notified: Map<Long, Long>): List<Recurring> =
        items
            .filter { it.state == RecurringState.ACTIVE && it.remindDaysBefore > 0 }
            .filter { !today.isBefore(it.nextDate().minusDays(it.remindDaysBefore.toLong())) && !today.isAfter(it.nextDate()) }
            .filter { notified[it.id] != today.toEpochDay() }
            .sortedBy { it.nextEpochDay }

    /** Заголовок уведомления по числу дней до списания. */
    fun reminderTitle(daysLeft: Long): String = when {
        daysLeft < 0 -> "Списание просрочено на ${-daysLeft} дн."
        daysLeft == 0L -> "Сегодня списание"
        daysLeft == 1L -> "Завтра списание"
        else -> "Через $daysLeft дн. списание"
    }
}
