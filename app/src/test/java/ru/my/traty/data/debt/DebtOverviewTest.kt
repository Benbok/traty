package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate
import java.time.ZoneId

class DebtOverviewTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 16)
    private val loanCat = 9L

    // Рассрочка 60 000 на 6 месяцев, первый платёж 1 ноября — уже просрочен
    private val tv = Debt(
        id = 1, title = "Телевизор", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
    )

    // Аванс 20 000 за холодильник 50 000, без графика
    private val fridge = Debt(id = 2, title = "Холодильник", categoryId = 2, totalKopecks = 5_000_000)

    // Кредит 120 000 под 20 % на 12 месяцев с 1 декабря
    private val car = Debt(
        id = 3, title = "Машина", categoryId = 3, kind = DebtKind.LOAN.key, totalKopecks = 12_000_000,
        rateBasisPoints = 2000, paymentsCount = 12, firstDueEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(),
    )

    private fun row(debtId: Long, expenseId: Long, kopecks: Long, day: LocalDate, part: String = PaymentPart.PRINCIPAL) =
        DebtPaymentRow(debtId, expenseId, part, kopecks, day.atStartOfDay(zone).toInstant().toEpochMilli())

    private fun sub(id: Long, title: String, kopecks: Long, categoryId: Long, state: String = RecurringState.ACTIVE) =
        Recurring(id = id, title = title, amountKopecks = kopecks, categoryId = categoryId, periodUnit = "month", nextEpochDay = today.toEpochDay(), state = state)

    private fun overview(
        debts: List<Debt>,
        payments: List<DebtPaymentRow> = emptyList(),
        recurring: List<Recurring> = emptyList(),
    ) = DebtOverviews.of(debts, payments, recurring, loanCat, today, zone)

    @Test
    fun emptyWhenNoDebts() {
        val o = overview(emptyList())
        assertTrue(o.isEmpty)
        assertEquals(0L, o.leftToPayKopecks)
        assertEquals(0L, o.dueSoonKopecks)
    }

    @Test
    fun totalsAcrossInstallmentAndPartial() {
        val o = overview(listOf(tv, fridge), listOf(row(2, 10, 2_000_000, LocalDate.of(2026, 11, 2))))
        // 60 000 по рассрочке + 30 000 остатка за холодильник
        assertEquals(9_000_000L, o.leftToPayKopecks)
        assertEquals(2_000_000L, o.paidKopecks)
        assertEquals(0, o.unknownPriceCount)
    }

    @Test
    fun overdueInstallmentGoesFirst() {
        val o = overview(listOf(fridge, tv), listOf(row(2, 10, 2_000_000, LocalDate.of(2026, 11, 2))))
        assertEquals(1, o.overdueCount)
        assertEquals(1_000_000L, o.overdueKopecks)
        assertEquals("Телевизор", o.lines.first().debt.title)
        assertTrue(o.lines.first().next?.overdue == true)
    }

    @Test
    fun dueSoonCountsOverdueAndNext30Days() {
        // Просрочен ноябрьский платёж, декабрьский (1.12) попадает в 30 дней с 16.11
        val o = overview(listOf(tv))
        assertEquals(2_000_000L, o.dueSoonKopecks)
    }

    @Test
    fun loanIncludesInterestInLeftToPayAndOverpayment() {
        val o = overview(listOf(car))
        val schedule = DebtSchedule.of(car)
        val interest = schedule.sumOf { it.interestKopecks }
        assertTrue(interest > 0)
        assertEquals(12_000_000L + interest, o.leftToPayKopecks)
        assertEquals(interest, o.plannedOverpaymentKopecks)
        assertEquals(0L, o.paidInterestKopecks)
        assertEquals(1, o.loans.size)
    }

    @Test
    fun paidInterestCountsForLoans() {
        val first = DebtSchedule.of(car).first()
        val day = LocalDate.of(2026, 12, 1)
        val payments = listOf(
            row(3, 20, first.interestKopecks, day, PaymentPart.INTEREST),
            row(3, 21, first.principalKopecks, day),
        )
        val o = DebtOverviews.of(listOf(car), payments, emptyList(), loanCat, day.plusDays(1), zone)
        assertEquals(first.interestKopecks, o.paidInterestKopecks)
        assertEquals(2, o.lines.single().next?.number)
    }

    @Test
    fun closedCancelledAndPaidOffAreLeftOut() {
        val closed = tv.copy(id = 4, state = DebtState.CLOSED)
        val cancelled = fridge.copy(id = 5, state = DebtState.CANCELLED)
        val paidOff = fridge.copy(id = 6)
        val o = overview(listOf(closed, cancelled, paidOff), listOf(row(6, 30, 5_000_000, today)))
        assertTrue(o.isEmpty)
        assertEquals(3, o.closedCount)
    }

    @Test
    fun owedToMeIsNotMyDebt() {
        val o = overview(listOf(fridge.copy(direction = DebtDirection.OWED_TO_ME)))
        assertTrue(o.isEmpty)
        assertEquals(0, o.closedCount)
    }

    @Test
    fun unknownPriceIsCountedSeparately() {
        val o = overview(listOf(fridge.copy(totalKopecks = null)), listOf(row(2, 10, 1_000_000, today)))
        assertEquals(1, o.unknownPriceCount)
        assertEquals(0L, o.leftToPayKopecks)
        assertNull(o.lines.single().leftToPayKopecks)
    }

    @Test
    fun subscriptionWithSamePaymentIsDuplicate() {
        val o = overview(listOf(tv), recurring = listOf(sub(1, "Рассрочка телевизор", 1_000_000, categoryId = 7)))
        assertEquals(1L, o.lines.single().duplicate?.id)
        assertEquals(1, o.duplicates.size)
    }

    @Test
    fun loanSubscriptionWithoutPurchaseIsListedSeparately() {
        val payment = DebtSchedule.of(car).first().totalKopecks
        val o = overview(
            listOf(car),
            recurring = listOf(
                sub(1, "Кредит", payment, categoryId = loanCat),                  // тот же кредит
                sub(2, "Ипотека", 3_500_000, categoryId = loanCat),               // только подпиской
                sub(3, "Старый кредит", 900_000, loanCat, RecurringState.PAUSED), // на паузе — не в счёт
                sub(4, "Кино", payment, categoryId = 100),                        // другая сумма не важна: нет признаков
            ),
        )
        assertEquals(1L, o.lines.single().duplicate?.id)
        assertEquals(listOf(2L), o.subscriptionOnly.map { it.id })
    }
}
