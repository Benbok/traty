package ru.my.traty.data.apartments

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal data class ConsumptionPeriodResult(
    val milli: Long,
    val estimated: Boolean,
    val complete: Boolean,
)

/** Суммирует расход приборов за их время учёта. Части интервалов оцениваются по средней скорости. */
internal object ConsumptionPeriod {
    fun calculate(series: List<RegisterSeries>, from: Long, to: Long): ConsumptionPeriodResult? {
        if (from >= to) return null
        var total = BigDecimal.ZERO
        var estimated = false
        var complete = true
        val covered = mutableListOf<Pair<Long, Long>>()

        for (register in series) {
            val start = maxOf(from, register.accountingFrom ?: from)
            val end = minOf(to, register.accountingTo ?: to)
            if (start >= end) continue

            val intervals = Consumption.intervals(register.points)
            val parts = intervals.mapNotNull { interval ->
                val overlapStart = maxOf(start, interval.from)
                val overlapEnd = minOf(end, interval.to)
                if (overlapStart < overlapEnd) Triple(interval, overlapStart, overlapEnd) else null
            }
            // Последовательные замеры одного тарифа образуют непрерывную цепочку.
            if (parts.firstOrNull()?.second != start || parts.lastOrNull()?.third != end) complete = false
            for ((interval, overlapStart, overlapEnd) in parts) {
                covered += overlapStart to overlapEnd
                val amount = BigDecimal.valueOf(interval.milli)
                val contribution = if (interval.from == overlapStart && interval.to == overlapEnd) amount
                else {
                    estimated = true
                    amount.multiply(BigDecimal.valueOf(overlapEnd - overlapStart))
                        .divide(BigDecimal.valueOf(interval.to - interval.from), MathContext.DECIMAL128)
                }
                total = total.add(contribution)
            }
        }

        if (covered.isEmpty()) return null
        var coveredUntil = from
        for ((start, end) in covered.sortedBy { it.first }) {
            if (start > coveredUntil) complete = false
            coveredUntil = maxOf(coveredUntil, end)
        }
        return try {
            ConsumptionPeriodResult(
                total.setScale(0, RoundingMode.HALF_UP).longValueExact(),
                estimated = estimated,
                complete = complete && coveredUntil == to,
            )
        } catch (_: ArithmeticException) {
            null
        }
    }
}
