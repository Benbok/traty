package ru.my.traty.ui.stats

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Сколько дней в диапазоне «с даты по дату», обе даты включительно. */
fun rangeLengthDays(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to) + 1

/** Предыдущий диапазон такой же длины, вплотную перед [from]: для сравнения «с прошлым». */
fun previousRange(from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> {
    val len = rangeLengthDays(from, to)
    return from.minusDays(len) to from.minusDays(1)
}

/** Сдвиг окна на его длину: [direction] = 1 — вперёд, -1 — назад; без зазоров и пересечений. */
fun shiftRange(from: LocalDate, to: LocalDate, direction: Int): Pair<LocalDate, LocalDate> {
    val shift = rangeLengthDays(from, to) * direction
    return from.plusDays(shift) to to.plusDays(shift)
}

/** Что выбрано в переключателе Статистики. «Период» — расчётный месяц, остальное — режим дат. */
enum class StatsSpan(val title: String) {
    PERIOD("Период"),
    DAY("День"),
    WEEK("Неделя"),
    DATES("Даты"),
}

/** Один день [d]. */
fun dayRange(d: LocalDate): Pair<LocalDate, LocalDate> = d to d

/** Неделя с понедельника по воскресенье, в которую попадает [d]. */
fun weekRange(d: LocalDate): Pair<LocalDate, LocalDate> {
    val monday = d.minusDays((d.dayOfWeek.value - 1).toLong())
    return monday to monday.plusDays(6)
}

/** Какой пункт переключателя подсветить для диапазона [range] (null — расчётный период). */
fun spanOf(range: Pair<LocalDate, LocalDate>?): StatsSpan = when {
    range == null -> StatsSpan.PERIOD
    range.first == range.second -> StatsSpan.DAY
    range == weekRange(range.first) -> StatsSpan.WEEK
    else -> StatsSpan.DATES
}
