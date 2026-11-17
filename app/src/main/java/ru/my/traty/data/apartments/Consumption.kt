package ru.my.traty.data.apartments

import java.time.temporal.ChronoUnit

/** Только даты и значения: расчёты не зависят от Room, Android и ИИ. */
data class ReadingPoint(val measuredAt: Long, val valueMilli: Long)
data class ConsumptionInterval(val from: Long, val to: Long, val milli: Long) {
    val days: Long get() = ChronoUnit.DAYS.between(ReadingDates.decode(from), ReadingDates.decode(to))
    val daily: Double get() = milli.toDouble() / 1000 / days
}
data class ConsumptionComparison(val previous: ConsumptionInterval, val current: ConsumptionInterval) {
    val dailyChangePercent: Double? get() = previous.daily.takeIf { it > 0 }?.let { (current.daily - it) * 100 / it }
}
object Consumption {
    fun intervals(points: List<ReadingPoint>): List<ConsumptionInterval> {
        val sorted = points.sortedBy { it.measuredAt }
        require(sorted.all { it.valueMilli >= 0 && ReadingDates.normalized(it.measuredAt) }) { "Проверьте даты и значения показаний" }
        return sorted.zipWithNext { a, b ->
            require(a.measuredAt < b.measuredAt && b.valueMilli >= a.valueMilli) { "Показания должны возрастать по датам" }
            ConsumptionInterval(a.measuredAt, b.measuredAt, Math.subtractExact(b.valueMilli, a.valueMilli))
        }
    }
    fun compareLastTwo(points: List<ReadingPoint>): ConsumptionComparison? {
        val xs = intervals(points).takeLast(2)
        return if (xs.size == 2) ConsumptionComparison(xs[0], xs[1]) else null
    }
}

data class RegisterSeries(
    val registerId: Long, val meterId: Long, val code: String, val points: List<ReadingPoint>,
    val accountingFrom: Long?, val accountingTo: Long? = null, val archived: Boolean = false,
)
object ConsumptionGroups {
    /** Нет точных границ/покрытия — нет общего итога. Никакой интерполяции. */
    fun forRange(series: List<RegisterSeries>, from: Long, to: Long): ConsumptionInterval? {
        if (series.isEmpty() || from >= to) return null
        if (series.map { it.registerId }.distinct().size != series.size) return null
        if (series.groupBy { it.meterId }.values.any { rs -> rs.any { it.code == "TOTAL" } && rs.size > 1 }) return null
        var total = 0L
        for (s in series) {
            if (s.accountingFrom == null || s.accountingFrom > from ||
                (s.archived && s.accountingTo == null) || (s.accountingTo != null && s.accountingTo < to)) return null
            val start = s.points.singleOrNull { it.measuredAt == from } ?: return null
            val end = s.points.singleOrNull { it.measuredAt == to } ?: return null
            // Validate the complete history, including interior readings.
            Consumption.intervals(s.points)
            total = try { Math.addExact(total, Math.subtractExact(end.valueMilli, start.valueMilli)) }
                catch (_: ArithmeticException) { return null }
        }
        return ConsumptionInterval(from, to, total)
    }
    fun lastComparison(series: List<RegisterSeries>): ConsumptionComparison? {
        if (series.isEmpty()) return null
        val dates = series.map { it.points.map { p -> p.measuredAt }.toSet() }
            .reduce { a, b -> a intersect b }.sorted().takeLast(3)
        if (dates.size != 3) return null
        val a = forRange(series, dates[0], dates[1]) ?: return null
        val b = forRange(series, dates[1], dates[2]) ?: return null
        return ConsumptionComparison(a, b)
    }
}
