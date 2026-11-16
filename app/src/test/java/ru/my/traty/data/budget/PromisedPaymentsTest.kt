package ru.my.traty.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.PaidPart
import ru.my.traty.data.debt.PaymentPart
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

class PromisedPaymentsTest {
    private val period = BillingPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private val today = LocalDate.of(2026, 11, 15)

    // 6 платежей по 10 000, первый 20 ноября
    private val phone = Debt(
        id = 1, title = "Телефон", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
    )

    private fun sub(title: String, rub: Long, next: LocalDate, state: String = RecurringState.ACTIVE, varies: Boolean = false) = Recurring(
        title = title, amountKopecks = rub * 100, categoryId = 1, periodUnit = "month",
        nextEpochDay = next.toEpochDay(), state = state, amountVaries = varies,
    )

    @Test
    fun debtAndSubscriptionsUntilEndOfPeriod() {
        val p = PromisedPayments.of(
            listOf(phone to emptyList()),
            listOf(sub("Музыка", 199, LocalDate.of(2026, 11, 25)), sub("Связь", 500, LocalDate.of(2026, 12, 3))),
            period, today,
        )
        // Телефон 20.11 и музыка 25.11; связь спишется уже в декабре
        assertEquals(listOf("Телефон", "Музыка"), p.items.map { it.title })
        assertEquals(1_000_000L + 19_900L, p.totalKopecks)
        assertTrue(p.items.first().fromDebt)
    }

    @Test
    fun paidPaymentIsNotPromisedAgain() {
        val paid = listOf(PaidPart(LocalDate.of(2026, 11, 14).toEpochDay(), PaymentPart.PRINCIPAL, 1_000_000))
        val p = PromisedPayments.of(listOf(phone to paid), emptyList(), period, today)
        assertTrue(p.isEmpty)
    }

    @Test
    fun overduePaymentCountsAndIsMarked() {
        val late = phone.copy(firstDueEpochDay = LocalDate.of(2026, 11, 10).toEpochDay())
        val item = PromisedPayments.of(listOf(late to emptyList()), emptyList(), period, today).items.single()
        assertTrue(item.overdue)
        assertEquals(1_000_000L, item.amountKopecks)
    }

    @Test
    fun onlyCurrentPeriod() {
        val future = BillingPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))
        val past = BillingPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val subs = listOf(sub("Музыка", 199, LocalDate.of(2026, 12, 5)))
        assertTrue(PromisedPayments.of(listOf(phone to emptyList()), subs, future, today).isEmpty)
        assertTrue(PromisedPayments.of(listOf(phone to emptyList()), subs, past, today).isEmpty)
    }

    @Test
    fun pausedSubscriptionAndClosedDebtAreSkipped() {
        val subs = listOf(sub("Музыка", 199, LocalDate.of(2026, 11, 25), state = RecurringState.PAUSED))
        val closed = phone.copy(state = "closed")
        assertTrue(PromisedPayments.of(listOf(closed to emptyList()), subs, period, today).isEmpty)
    }

    @Test
    fun variableAmountMakesTotalApproximate() {
        val p = PromisedPayments.of(emptyList(), listOf(sub("Свет", 1_200, LocalDate.of(2026, 11, 28), varies = true)), period, today)
        assertTrue(p.approximate)
        assertFalse(PromisedPayments.of(emptyList(), listOf(sub("Музыка", 199, LocalDate.of(2026, 11, 28))), period, today).approximate)
    }

    @Test
    fun weeklySubscriptionRepeatsInsidePeriod() {
        val weekly = sub("Бассейн", 300, LocalDate.of(2026, 11, 16)).copy(periodUnit = "week")
        val p = PromisedPayments.of(emptyList(), listOf(weekly), period, today)
        // 16, 23 и 30 ноября
        assertEquals(3, p.items.size)
        assertEquals(90_000L, p.totalKopecks)
    }

    @Test
    fun loanSubscriptionDuplicatingPurchaseCountsOnce() {
        // Рассрочка за телефон заведена ещё и подпиской на ту же сумму 20-го числа
        val dup = sub("Рассрочка телефон", 10_000, LocalDate.of(2026, 11, 20)).copy(id = 5)
        val music = sub("Музыка", 199, LocalDate.of(2026, 11, 25)).copy(id = 6)
        val p = PromisedPayments.of(listOf(phone to emptyList()), listOf(dup, music), period, today)
        assertEquals(listOf("Телефон", "Музыка"), p.items.map { it.title })
        assertEquals(1_000_000L + 19_900L, p.totalKopecks)
    }

    @Test
    fun subscriptionStaysWhenPurchaseIsClosed() {
        // Покупку закрыли, а подписка осталась: её платёж по-прежнему предстоит
        val dup = sub("Рассрочка телефон", 10_000, LocalDate.of(2026, 11, 20)).copy(id = 5)
        val p = PromisedPayments.of(listOf(phone.copy(state = "closed") to emptyList()), listOf(dup), period, today)
        assertEquals(listOf("Рассрочка телефон"), p.items.map { it.title })
    }

    @Test
    fun loanCategorySubscriptionWithOtherNameIsDuplicate() {
        val bank = sub("Банк", 10_000, LocalDate.of(2026, 11, 20)).copy(id = 5, categoryId = 9)
        val withLoanCategory = PromisedPayments.of(listOf(phone.copy(categoryId = 2) to emptyList()), listOf(bank), period, today, loanCategoryId = 9)
        assertEquals(listOf("Телефон"), withLoanCategory.items.map { it.title })
        // Без признаков, кроме суммы, подписку не трогаем
        val unknown = PromisedPayments.of(listOf(phone.copy(categoryId = 2) to emptyList()), listOf(bank), period, today)
        assertEquals(2, unknown.items.size)
    }
}
