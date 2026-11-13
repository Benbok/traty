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
