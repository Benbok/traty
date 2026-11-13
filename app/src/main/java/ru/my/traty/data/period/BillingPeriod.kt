package ru.my.traty.data.period

import java.time.LocalDate
import java.time.ZoneId

const val MIN_PERIOD_START_DAY = 1
const val MAX_PERIOD_START_DAY = 28

/**
 * Расчётный период: от [startDate] до [endDate] включительно.
 * При дне начала 1 совпадает с календарным месяцем.
 */
data class BillingPeriod(val startDate: LocalDate, val endDate: LocalDate) {
    fun startMillis(zone: ZoneId): Long = startDate.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Начало дня, следующего за [endDate]: правая граница полуинтервала. */
    fun endMillisExclusive(zone: ZoneId): Long =
        endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)

    fun previous(startDay: Int): BillingPeriod = containing(startDate.minusDays(1), startDay)

    fun next(startDay: Int): BillingPeriod = containing(endDate.plusDays(1), startDay)

    companion object {
        fun containing(date: LocalDate, startDay: Int): BillingPeriod {
            val day = startDay.coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY)
            val monthStart = date.withDayOfMonth(1)
            val start = if (date.dayOfMonth >= day) {
                monthStart.withDayOfMonth(day)
            } else {
                monthStart.minusMonths(1).withDayOfMonth(day)
            }
            return BillingPeriod(start, start.plusMonths(1).minusDays(1))
        }
    }
}
