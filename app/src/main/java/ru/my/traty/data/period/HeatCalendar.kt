package ru.my.traty.data.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** День теплового календаря: сумма трат и насыщенность цвета [level] от 0 (не тратили) до [HeatCalendar.LEVELS]. */
data class HeatDay(val date: LocalDate, val total: Long, val count: Int, val level: Int)

/** Календарь периода: [leadingBlanks] пустых клеток перед первым днём (неделя начинается с понедельника). */
data class HeatMonth(val leadingBlanks: Int, val days: List<HeatDay>) {
    val maxTotal: Long get() = days.maxOfOrNull { it.total } ?: 0
}

object HeatCalendar {
    const val LEVELS = 4

    /** Самый длинный отрезок, который ещё помещается в календарь на экране. */
    const val MAX_DAYS = 62

    /**
     * Дни с [start] по [end] с суммами из [spends] (пары «день траты — сумма»).
     * Уровень — доля от самого дорогого дня: до 25% — 1, до 50% — 2 и так далее.
     * null — отрезок длиннее [MAX_DAYS] или перепутан.
     */
    fun build(start: LocalDate, end: LocalDate, spends: List<Pair<LocalDate, Long>>): HeatMonth? {
        if (end.isBefore(start)) return null
        val n = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        if (n > MAX_DAYS) return null
        val totals = LongArray(n)
        val counts = IntArray(n)
        for ((d, amount) in spends) {
            val i = ChronoUnit.DAYS.between(start, d).toInt()
            if (i in 0 until n) {
                totals[i] += amount
                counts[i]++
            }
        }
        val max = totals.maxOrNull() ?: 0
        val days = (0 until n).map { i ->
            val t = totals[i]
            val level = if (t <= 0 || max <= 0) 0 else ceil(LEVELS * t.toDouble() / max).toInt().coerceIn(1, LEVELS)
            HeatDay(start.plusDays(i.toLong()), t, counts[i], level)
        }
        return HeatMonth(start.dayOfWeek.value - 1, days)
    }
}
