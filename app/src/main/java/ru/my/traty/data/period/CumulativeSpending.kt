package ru.my.traty.data.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Накопленные траты по дням периода: [points][i] — сколько потрачено с начала периода по i-й день включительно.
 * Для идущего периода точки есть только до сегодня.
 */
data class CumulativeLine(val start: LocalDate, val daysTotal: Int, val points: List<Long>) {
    val last: Long get() = points.lastOrNull() ?: 0
}

/** «Обгоняю / отстаю»: сколько потрачено к этому дню сейчас и к такому же дню прошлого периода. */
data class PaceVsPrevious(val day: Int, val current: Long, val previous: Long) {
    val diff: Long get() = current - previous
}

object CumulativeSpending {
    /**
     * [spends] — пары (день траты, сумма). Траты вне периода пропускаются.
     * [today] обрезает идущий период; для прошедшего — все дни.
     */
    fun line(start: LocalDate, end: LocalDate, today: LocalDate, spends: List<Pair<LocalDate, Long>>): CumulativeLine {
        val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val shown = when {
            today.isBefore(start) -> 0
            today.isAfter(end) -> total
            else -> (ChronoUnit.DAYS.between(start, today) + 1).toInt()
        }
        val perDay = LongArray(total)
        for ((d, amount) in spends) {
            val i = ChronoUnit.DAYS.between(start, d).toInt()
            if (i in 0 until total) perDay[i] += amount
        }
        var acc = 0L
        val points = (0 until shown).map { i -> acc += perDay[i]; acc }
        return CumulativeLine(start, total, points)
    }

    /**
     * Сравнение на последний показанный день текущей линии. Если прошлый период короче,
     * берётся его последний день. null — сравнивать не с чем.
     */
    fun vsPrevious(current: CumulativeLine, previous: CumulativeLine): PaceVsPrevious? {
        if (current.points.isEmpty() || previous.points.isEmpty()) return null
        val i = current.points.lastIndex
        return PaceVsPrevious(i + 1, current.points[i], previous.points[minOf(i, previous.points.lastIndex)])
    }
}
