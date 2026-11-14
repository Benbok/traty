package ru.my.traty.data.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Темп трат за период: средняя трата в день и, пока период не кончился, прогноз на его конец.
 * [forecast] == null — период уже прошёл (или ещё не начался), прогнозировать нечего.
 */
data class SpendingPace(
    val spent: Long,
    /** Сколько дней периода прошло, включая сегодня (для прошедшего периода — все дни). */
    val daysPassed: Int,
    val daysTotal: Int,
    val dailyAverage: Long,
    val forecast: Long?,
) {
    val daysLeft: Int get() = daysTotal - daysPassed
}

object SpendingPaces {
    /**
     * Темп за дни с [start] по [end] включительно на дату [today].
     * Прогноз — линейный: сколько уже потрачено, плюс средняя в день на каждый оставшийся день.
     * Будущий период — null.
     */
    fun of(start: LocalDate, end: LocalDate, today: LocalDate, spent: Long): SpendingPace? {
        if (today.isBefore(start)) return null
        val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val finished = today.isAfter(end)
        val passed = if (finished) total else (ChronoUnit.DAYS.between(start, today) + 1).toInt()
        val avg = spent / passed
        val forecast = if (finished) null else spent + spent * (total - passed) / passed
        return SpendingPace(spent, passed, total, avg, forecast)
    }

    fun of(period: BillingPeriod, today: LocalDate, spent: Long): SpendingPace? =
        of(period.startDate, period.endDate, today, spent)
}
