package ru.my.traty.data

import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Как изменилась одна категория: сейчас, раньше, разница в копейках и в % (null — раньше трат не было). */
data class CategoryChange(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val current: Long,
    val previous: Long,
) {
    val diff: Long get() = current - previous
    val percent: Int? get() = if (previous == 0L) null else (diff * 100.0 / previous).roundToInt()
}

/** Сравнение двух периодов по категориям: рост и падение в ₽ и %, топ изменений. */
object PeriodComparison {
    /**
     * Все категории, что были хотя бы в одном из периодов. Категория, в которой тратили только раньше,
     * тоже попадает: «на кафе — минус 100%» — тоже изменение. Порядок — по модулю разницы.
     */
    fun changes(current: List<CategoryTotal>, previous: List<CategoryTotal>): List<CategoryChange> {
        val prev = previous.associateBy { it.categoryId }
        val cur = current.associateBy { it.categoryId }
        return (cur.keys + prev.keys).map { id ->
            val c = cur[id]
            val p = prev[id]
            val ref = c ?: p!!
            CategoryChange(id, ref.name, ref.emoji, c?.total ?: 0, p?.total ?: 0)
        }
            .filter { it.diff != 0L }
            .sortedWith(compareByDescending<CategoryChange> { abs(it.diff) }.thenBy { it.name })
    }

    /** Самые заметные изменения (по модулю разницы в рублях). */
    fun top(current: List<CategoryTotal>, previous: List<CategoryTotal>, n: Int = 3): List<CategoryChange> =
        changes(current, previous).take(n)

    /**
     * С какими днями прошлого периода честно сравнивать [period] на дату [today].
     * Пока период идёт (сегодня 5-й день), берём первые 5 дней прошлого периода — иначе в середине месяца
     * всё выглядит «упавшим». Прошедший или будущий период сравнивается с прошлым целиком.
     */
    fun comparableWindow(period: BillingPeriod, previous: BillingPeriod, today: LocalDate): Pair<LocalDate, LocalDate> {
        if (today !in period) return previous.startDate to previous.endDate
        val passed = ChronoUnit.DAYS.between(period.startDate, today)
        val end = previous.startDate.plusDays(passed)
        return previous.startDate to (if (end.isAfter(previous.endDate)) previous.endDate else end)
    }
}
