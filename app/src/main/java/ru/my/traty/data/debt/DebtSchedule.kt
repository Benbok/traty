package ru.my.traty.data.debt

import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.data.recurring.RecurrencePeriod
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Один платёж по графику. [number] с 1. Суммы в копейках. */
data class ScheduledPayment(
    val number: Int,
    val date: LocalDate,
    val principalKopecks: Long,
    val interestKopecks: Long,
) {
    val totalKopecks: Long get() = principalKopecks + interestKopecks
}

/**
 * График платежей покупки. Считается только из полей [Debt], ничего не хранит.
 *
 * - Рассрочка: цена делится на N платежей, округлённых до рубля, остаток — в последнем.
 * - Кредит: аннуитет (равные платежи), проценты каждый период на остаток долга.
 *   Последний платёж выравнивается, чтобы остаток стал ровно нулём.
 * - Если пользователь назвал платёж по договору ([Debt.paymentKopecks]), берётся он: у банка своё округление.
 * - Частичная оплата без графика, неизвестная цена или число платежей — пустой график.
 */
object DebtSchedule {
    private val mc = MathContext(20, RoundingMode.HALF_EVEN)
    private val hundredPercent = BigDecimal(10_000)

    fun of(debt: Debt): List<ScheduledPayment> {
        val total = debt.totalKopecks?.takeIf { it > 0 } ?: return emptyList()
        val count = debt.paymentsCount?.takeIf { it > 0 } ?: return emptyList()
        val first = debt.firstDueEpochDay?.let(LocalDate::ofEpochDay) ?: return emptyList()
        val dates = dates(first, debt.period, count)
        val rate = if (debt.debtKind == DebtKind.LOAN) periodRate(debt.rateBasisPoints, debt.period) else BigDecimal.ZERO
        val payment = debt.paymentKopecks?.takeIf { it > 0 }
            ?: if (rate.signum() == 0) evenPayment(total, count) else annuity(total, rate, count)
        return build(total, rate, payment, dates)
    }

    /** Сколько всего уйдёт сверх цены по графику (переплата). */
    fun overpaymentKopecks(debt: Debt): Long = of(debt).sumOf { it.interestKopecks }

    /** Платёж по аннуитету: P · r · q / (q − 1), где q = (1 + r)^n. */
    fun annuity(principalKopecks: Long, periodRate: BigDecimal, count: Int): Long {
        if (periodRate.signum() == 0) return evenPayment(principalKopecks, count)
        val q = BigDecimal.ONE.add(periodRate, mc).pow(count, mc)
        val a = BigDecimal(principalKopecks).multiply(periodRate, mc).multiply(q, mc).divide(q.subtract(BigDecimal.ONE, mc), mc)
        return a.setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * Ставка за один период из годовой ставки в сотых долях процента.
     * Месяц — 1/12 года, год — целиком, недели и дни — по 365 дням.
     */
    fun periodRate(rateBasisPoints: Int, period: RecurrencePeriod): BigDecimal {
        if (rateBasisPoints <= 0) return BigDecimal.ZERO
        val yearly = BigDecimal(rateBasisPoints).divide(hundredPercent, mc)
        val every = BigDecimal(period.every)
        return when (period.unit) {
            PeriodUnit.MONTH -> yearly.multiply(every, mc).divide(BigDecimal(12), mc)
            PeriodUnit.YEAR -> yearly.multiply(every, mc)
            PeriodUnit.WEEK -> yearly.multiply(every, mc).multiply(BigDecimal(7), mc).divide(BigDecimal(365), mc)
            PeriodUnit.DAY -> yearly.multiply(every, mc).divide(BigDecimal(365), mc)
        }
    }

    private fun build(total: Long, rate: BigDecimal, payment: Long, dates: List<LocalDate>): List<ScheduledPayment> {
        val result = mutableListOf<ScheduledPayment>()
        var balance = total
        for ((i, date) in dates.withIndex()) {
            if (balance <= 0) break
            val interest = BigDecimal(balance).multiply(rate, mc).setScale(0, RoundingMode.HALF_UP).toLong()
            val last = i == dates.lastIndex
            // Платёж меньше процентов — график не сойдётся; тело всё равно гасится хотя бы на копейку
            val principal = if (last) balance else (payment - interest).coerceIn(1, balance)
            result += ScheduledPayment(i + 1, date, principal, interest)
            balance -= principal
        }
        return result
    }

    private fun dates(first: LocalDate, period: RecurrencePeriod, count: Int): List<LocalDate> {
        // Считаем от первой даты, чтобы 31-е не «съезжало» на 28-е после февраля
        return (0 until count).map { n ->
            when (period.unit) {
                PeriodUnit.DAY -> first.plusDays(n.toLong() * period.every)
                PeriodUnit.WEEK -> first.plusWeeks(n.toLong() * period.every)
                PeriodUnit.MONTH -> first.plusMonths(n.toLong() * period.every)
                PeriodUnit.YEAR -> first.plusYears(n.toLong() * period.every)
            }
        }
    }

    /** Равный платёж рассрочки округляется вниз до рубля, остаток — в последнем платеже. */
    private fun evenPayment(total: Long, count: Int): Long {
        val even = total / count
        val rubles = even / 100 * 100
        return if (rubles > 0) rubles else even.coerceAtLeast(1)
    }
}
