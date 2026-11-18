package ru.my.traty.data.apartments

internal object ConsumptionTrend {
    /** Привязываем расход к дате второго замера, сохраняя фактический интервал. */
    fun forPeriod(points: List<ReadingPoint>, from: Long, to: Long): List<ConsumptionInterval> =
        Consumption.intervals(points).filter { it.to in from..to }

    fun forResource(series: List<RegisterSeries>, from: Long, to: Long): List<ConsumptionInterval> {
        val commonDates = series.map { register -> register.points.map { it.measuredAt }.toSet() }
            .reduceOrNull { dates, next -> dates intersect next }.orEmpty().sorted()
        return commonDates.zipWithNext().mapNotNull { (start, end) ->
            if (end in from..to) ConsumptionGroups.forRange(series, start, end) else null
        }
    }
}
