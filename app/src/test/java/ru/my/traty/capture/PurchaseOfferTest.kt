package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import java.time.LocalDate

class PurchaseOfferTest {
    private val fridge = Debt(id = 1, title = "Холодильник", place = "Ozon", categoryId = 5)
    private val today = LocalDate.of(2026, 11, 15)
    private val partial = PurchaseTerms(kind = PurchaseTerms.PARTIAL)
    private val topup = PurchaseTerms(kind = PurchaseTerms.TOPUP)

    private fun offer(terms: PurchaseTerms, expenseId: Long? = 7, amount: Long = 2_000_000) = PurchaseOffer(
        expenseId = expenseId, amountKopecks = amount, title = "Телевизор", place = null,
        categoryId = 5, subcategoryId = null, day = today, terms = terms,
    )

    @Test
    fun advanceStartsNewPurchase() {
        assertEquals(PurchaseDecision.Create, PurchaseOffers.decide(partial, "холодильник", "Ozon", emptyList(), hasPayment = true))
    }

    @Test
    fun topUpAttachesToOpenPurchase() {
        assertEquals(PurchaseDecision.Attach(fridge), PurchaseOffers.decide(topup, "холодильник", "Ozon", listOf(fridge), hasPayment = true))
        assertEquals(PurchaseDecision.Attach(fridge), PurchaseOffers.decide(topup, null, "Ozon", listOf(fridge), hasPayment = true))
        assertEquals(PurchaseDecision.None, PurchaseOffers.decide(topup, "холодильник", "Ozon", emptyList(), hasPayment = true))
    }

    @Test
    fun plainExpenseAttachesOnlyWithItemAndShop() {
        assertEquals(PurchaseDecision.Attach(fridge), PurchaseOffers.decide(null, "холодильник", "Ozon", listOf(fridge), hasPayment = true))
        assertEquals(PurchaseDecision.None, PurchaseOffers.decide(null, "холодильник", null, listOf(fridge), hasPayment = true))
        assertEquals(PurchaseDecision.None, PurchaseOffers.decide(null, "молоко", "Ozon", listOf(fridge), hasPayment = true))
    }

    @Test
    fun nextInstallmentWithoutNewPriceIsSamePurchase() {
        val installment = PurchaseTerms(kind = PurchaseTerms.INSTALLMENT)
        assertEquals(PurchaseDecision.Attach(fridge), PurchaseOffers.decide(installment, "холодильник", null, listOf(fridge), hasPayment = true))
    }

    @Test
    fun loanPaymentWithoutTermsIsOrdinary() {
        assertEquals(PurchaseDecision.None, PurchaseOffers.decide(PurchaseTerms(kind = PurchaseTerms.LOAN), "кредит", null, emptyList(), hasPayment = true))
        val unpaid = PurchaseTerms(kind = PurchaseTerms.LOAN, total = 80000.0, count = 12, rate = 20.0)
        assertEquals(PurchaseDecision.Create, PurchaseOffers.decide(unpaid, "ноутбук", null, emptyList(), hasPayment = false))
    }

    @Test
    fun formBuildsInstallmentStartingToday() {
        val o = offer(PurchaseTerms(kind = PurchaseTerms.INSTALLMENT, total = 60000.0, count = 6))
        val d = checkNotNull(PurchaseForm.from(o).toDebt(o))
        assertEquals(DebtKind.INSTALLMENT, d.debtKind)
        assertEquals(6_000_000L, d.totalKopecks)
        assertEquals(6, d.paymentsCount)
        assertEquals(today.toEpochDay(), d.firstDueEpochDay)
    }

    @Test
    fun formBuildsUnpaidLoanStartingNextMonth() {
        val o = offer(PurchaseTerms(kind = PurchaseTerms.LOAN, total = 80000.0, count = 12, rate = 17.9), expenseId = null, amount = 0)
        val d = checkNotNull(PurchaseForm.from(o).toDebt(o))
        assertEquals(1790, d.rateBasisPoints)
        assertEquals(today.plusMonths(1).toEpochDay(), d.firstDueEpochDay)
    }

    @Test
    fun formNeedsCountForInstallmentAndPriceWithoutPayment() {
        val o = offer(PurchaseTerms(kind = PurchaseTerms.INSTALLMENT, total = 60000.0))
        assertNull(PurchaseForm.from(o).toDebt(o))
        val unpaid = offer(partial, expenseId = null, amount = 0)
        assertNull(PurchaseForm.from(unpaid).toDebt(unpaid))
        // Аванс без цены можно: остаток посчитается, когда цена станет известна
        val withPayment = offer(partial)
        assertEquals(null, checkNotNull(PurchaseForm.from(withPayment).toDebt(withPayment)).totalKopecks)
    }

    @Test
    fun bnplEveryTwoWeeks() {
        val o = offer(PurchaseTerms(kind = PurchaseTerms.INSTALLMENT, total = 12000.0, count = 4, period = "2weeks"))
        val d = checkNotNull(PurchaseForm.from(o).toDebt(o))
        assertEquals("week", d.periodUnit)
        assertEquals(2, d.periodEvery)
    }

    @Test
    fun titleFallsBackToSubcategoryAndPlace() {
        assertEquals("Холодильник", PurchaseOffers.titleOf("холодильник", "Ozon", "Ozon"))
        assertEquals("Ozon", PurchaseOffers.titleOf(null, "", "Ozon"))
        assertEquals("Покупка", PurchaseOffers.titleOf(null, null, null))
    }
}
