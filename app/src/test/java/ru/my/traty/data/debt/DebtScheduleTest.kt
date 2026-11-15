package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.recurring.PeriodUnit
import java.time.LocalDate

class DebtScheduleTest {
    private val start = LocalDate.of(2026, 11, 15)

    private fun debt(
        kind: DebtKind,
        total: Long?,
        count: Int?,
        rate: Int = 0,
        payment: Long? = null,
        unit: PeriodUnit = PeriodUnit.MONTH,
        every: Int = 1,
        first: LocalDate? = start,
    ) = Debt(
        title = "Телевизор", categoryId = 1, kind = kind.key, totalKopecks = total, paymentsCount = count,
        rateBasisPoints = rate, paymentKopecks = payment, periodUnit = unit.key, periodEvery = every,
        firstDueEpochDay = first?.toEpochDay(),
    )

    @Test
    fun installmentSplitsEvenly() {
        val s = DebtSchedule.of(debt(DebtKind.INSTALLMENT, 6_000_000, 6))
        assertEquals(6, s.size)
        assertTrue(s.all { it.principalKopecks == 1_000_000L && it.interestKopecks == 0L })
        assertEquals(LocalDate.of(2027, 4, 15), s.last().date)
    }

    @Test
    fun installmentRemainderGoesToLastPayment() {
        val s = DebtSchedule.of(debt(DebtKind.INSTALLMENT, 1_000_000, 3))
        assertEquals(listOf(333_300L, 333_300L, 333_400L), s.map { it.principalKopecks })
        assertEquals(1_000_000L, s.sumOf { it.totalKopecks })
    }

    @Test
    fun monthlyDatesDoNotDriftAfterFebruary() {
        val s = DebtSchedule.of(debt(DebtKind.INSTALLMENT, 300_000, 3, first = LocalDate.of(2027, 1, 31)))
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)), s.map { it.date })
    }

    @Test
    fun twoWeekPartsLikeBnpl() {
        val s = DebtSchedule.of(debt(DebtKind.INSTALLMENT, 1_200_000, 4, unit = PeriodUnit.WEEK, every = 2))
        assertEquals(listOf(0L, 14L, 28L, 42L), s.map { it.date.toEpochDay() - start.toEpochDay() })
        assertTrue(s.all { it.principalKopecks == 300_000L })
    }

    @Test
    fun loanIsAnnuity() {
        // 80 000 ₽ под 20 % на 12 месяцев: платёж 7 410,76 ₽, переплата 8 929,12 ₽
        val d = debt(DebtKind.LOAN, 8_000_000, 12, rate = 2000)
        val s = DebtSchedule.of(d)
        assertEquals(12, s.size)
        assertTrue(s.dropLast(1).all { it.totalKopecks == 741_076L })
        assertEquals(133_333L, s.first().interestKopecks)
        assertEquals(8_000_000L, s.sumOf { it.principalKopecks })
        assertEquals(892_912L, DebtSchedule.overpaymentKopecks(d))
    }

    @Test
    fun loanWithContractPaymentUsesIt() {
        val s = DebtSchedule.of(debt(DebtKind.LOAN, 8_000_000, 12, rate = 2000, payment = 750_000))
        assertTrue(s.dropLast(1).all { it.totalKopecks == 750_000L })
        assertEquals(8_000_000L, s.sumOf { it.principalKopecks })
    }

    @Test
    fun rateOnInstallmentIsIgnoredAndZeroRateLoanIsEven() {
        assertEquals(0L, DebtSchedule.overpaymentKopecks(debt(DebtKind.INSTALLMENT, 6_000_000, 6, rate = 2000)))
        val s = DebtSchedule.of(debt(DebtKind.LOAN, 6_000_000, 6))
        assertTrue(s.all { it.totalKopecks == 1_000_000L })
    }

    @Test
    fun noScheduleWithoutPriceCountOrDate() {
        assertTrue(DebtSchedule.of(debt(DebtKind.PARTIAL, 6_000_000, null)).isEmpty())
        assertTrue(DebtSchedule.of(debt(DebtKind.INSTALLMENT, null, 6)).isEmpty())
        assertTrue(DebtSchedule.of(debt(DebtKind.INSTALLMENT, 6_000_000, 6, first = null)).isEmpty())
    }
}
