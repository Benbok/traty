package ru.my.traty.data.recurring

import java.time.LocalDate

/** Единица периода регулярного платежа. [key] хранится в базе. */
enum class PeriodUnit(val key: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    companion object {
        /** Неизвестное значение из базы читаем как месяц: это самый частый период. */
        fun of(key: String?): PeriodUnit = entries.firstOrNull { it.key == key } ?: MONTH
    }
}

/**
 * «Раз в [every] [unit]»: раз в неделю, раз в месяц, раз в 45 дней.
 */
data class RecurrencePeriod(val unit: PeriodUnit, val every: Int = 1) {
    init {
        require(every >= 1) { "every должно быть >= 1" }
    }

    /**
     * Дата следующего списания после [date].
     * Месяцы и годы считаются календарно: с 31 января следующее — 28 (29) февраля, а с него дальше — 28 марта
     * (день не «возвращается»; для платежей 29–31-го числа это допустимая неточность).
     */
    fun advance(date: LocalDate): LocalDate = when (unit) {
        PeriodUnit.DAY -> date.plusDays(every.toLong())
        PeriodUnit.WEEK -> date.plusWeeks(every.toLong())
        PeriodUnit.MONTH -> date.plusMonths(every.toLong())
        PeriodUnit.YEAR -> date.plusYears(every.toLong())
    }

    /** Ближайшая дата списания не раньше [today]: пропущенные периоды отбрасываются. */
    fun nextOnOrAfter(date: LocalDate, today: LocalDate): LocalDate {
        var d = date
        while (d.isBefore(today)) d = advance(d)
        return d
    }

    /** Сколько платёж стоит за год, в копейках (для дней и недель — по 365 дням). */
    fun yearlyKopecks(amountKopecks: Long): Long = when (unit) {
        PeriodUnit.DAY -> amountKopecks * 365 / every
        PeriodUnit.WEEK -> amountKopecks * 365 / (7L * every)
        PeriodUnit.MONTH -> amountKopecks * 12 / every
        PeriodUnit.YEAR -> amountKopecks / every
    }

    /** Сколько платёж стоит в среднем за месяц, в копейках. */
    fun monthlyKopecks(amountKopecks: Long): Long = yearlyKopecks(amountKopecks) / 12

    /** Короткая подпись: «в мес.», «в нед.», «раз в 45 дн.». */
    fun label(): String = when {
        every == 1 -> when (unit) {
            PeriodUnit.DAY -> "в день"
            PeriodUnit.WEEK -> "в нед."
            PeriodUnit.MONTH -> "в мес."
            PeriodUnit.YEAR -> "в год"
        }
        else -> "раз в $every " + when (unit) {
            PeriodUnit.DAY -> "дн."
            PeriodUnit.WEEK -> "нед."
            PeriodUnit.MONTH -> "мес."
            PeriodUnit.YEAR -> "г."
        }
    }

    companion object {
        val WEEKLY = RecurrencePeriod(PeriodUnit.WEEK)
        val MONTHLY = RecurrencePeriod(PeriodUnit.MONTH)
        val YEARLY = RecurrencePeriod(PeriodUnit.YEAR)
    }
}
