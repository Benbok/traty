package ru.my.traty.data.recurring

import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate

fun Recurring.period(): RecurrencePeriod = RecurrencePeriod(PeriodUnit.of(periodUnit), periodEvery.coerceAtLeast(1))

fun Recurring.nextDate(): LocalDate = LocalDate.ofEpochDay(nextEpochDay)

/** Сколько этот платёж стоит за год — столько сэкономит его отмена. */
val Recurring.yearlyKopecks: Long get() = period().yearlyKopecks(amountKopecks)

/** Итоги по действующим платежам; платежи на паузе и отклонённые предложения не считаются. */
/** [approximate] — среди платежей есть с меняющейся суммой, итог приблизительный. */
data class RecurringTotals(
    val count: Int,
    val monthlyKopecks: Long,
    val yearlyKopecks: Long,
    val approximate: Boolean = false,
)

object RecurringSummary {
    fun totals(items: List<Recurring>): RecurringTotals {
        val active = items.filter { it.state == RecurringState.ACTIVE }
        return RecurringTotals(
            count = active.size,
            monthlyKopecks = active.sumOf { it.period().monthlyKopecks(it.amountKopecks) },
            yearlyKopecks = active.sumOf { it.yearlyKopecks },
            approximate = active.any { it.amountVaries },
        )
    }
}

/** Предложение, которое пользователь принял. Дата списания — ближайшая не раньше [today]. */
fun RecurringCandidate.toRecurring(today: LocalDate, state: String = RecurringState.ACTIVE): Recurring = Recurring(
    title = title,
    amountKopecks = amountKopecks,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    place = place,
    periodUnit = period.unit.key,
    periodEvery = period.every,
    nextEpochDay = period.nextOnOrAfter(nextDate, today).toEpochDay(),
    state = state,
    autoDetected = true,
    detectKey = key,
)
