package ru.my.traty.data.apartments

import java.math.BigInteger
import java.time.temporal.ChronoUnit

data class ReadingCheck(
    val previous: ReadingPoint?,
    val consumptionMilli: Long?,
    val error: String? = null,
    val unusuallyHigh: Boolean = false,
)

/** Проверка до сохранения; репозиторий повторно проверяет всю историю в транзакции. */
fun checkReading(date: Long, value: Long, history: List<ReadingPoint>): ReadingCheck {
    val previous = history.filter { it.measuredAt < date }.maxByOrNull { it.measuredAt }
    val next = history.filter { it.measuredAt > date }.minByOrNull { it.measuredAt }
    val error = when {
        value < 0 -> "Показание не может быть отрицательным"
        history.any { it.measuredAt == date } -> "За эту дату уже есть показание"
        previous != null && value < previous.valueMilli -> "Значение меньше предыдущего показания. Проверьте число и дату"
        next != null && value > next.valueMilli -> "Значение больше следующего показания в истории. Проверьте число и дату"
        else -> null
    }
    if (error != null || previous == null) return ReadingCheck(previous, null, error)
    val delta = value - previous.valueMilli
    val beforePrevious = history.filter { it.measuredAt < previous.measuredAt }.maxByOrNull { it.measuredAt }
        ?: return ReadingCheck(previous, delta)
    val priorDelta = previous.valueMilli - beforePrevious.valueMilli
    val days = ChronoUnit.DAYS.between(ReadingDates.decode(previous.measuredAt), ReadingDates.decode(date))
    val priorDays = ChronoUnit.DAYS.between(ReadingDates.decode(beforePrevious.measuredAt), ReadingDates.decode(previous.measuredAt))
    // Сравниваем средний суточный расход, а не суммы интервалов разной длины.
    val high = priorDelta > 0 && days > 0 && priorDays > 0 &&
        BigInteger.valueOf(delta).multiply(BigInteger.valueOf(priorDays)) >
        BigInteger.valueOf(priorDelta).multiply(BigInteger.valueOf(days)).multiply(BigInteger.valueOf(3))
    return ReadingCheck(previous, delta, unusuallyHigh = high)
}
