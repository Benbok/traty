package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchasePaymentTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun paymentsForPurchases() {
        assertTrue(PurchasePayment.isPurchasePayment("аванс за холодильник, купленный на Озоне вчера, 20 тысяч"))
        assertTrue(PurchasePayment.isPurchasePayment("пополнение или за холодильник Озону плюс 5000"))
        assertTrue(PurchasePayment.isPurchasePayment("предоплата кухня 30000"))
        assertTrue(PurchasePayment.isPurchasePayment("доплата за диван 7000"))
    }

    @Test
    fun salaryAdvanceIsNotPurchase() {
        assertFalse(PurchasePayment.isPurchasePayment("пришёл аванс 30000"))
        assertFalse(PurchasePayment.isPurchasePayment("аванс за ноябрь 40000"))
        assertFalse(PurchasePayment.isPurchasePayment("аванс за дежурства 12000"))
        assertFalse(PurchasePayment.isPurchasePayment("кофе 250"))
    }

    @Test
    fun offlineAdvanceForFridgeIsMarketplaceExpense() {
        val e = OfflineEntryParser.parse("аванс за холодильник, купленный на Озоне вчера, 20 тысяч", emptyList(), today).single()
        assertEquals(EntryType.EXPENSE, e.entryType)
        assertEquals(20000.0, e.amount, 0.0)
        assertEquals("Маркетплейсы", e.category)
        assertEquals("📦", e.emoji)
        assertEquals("Ozon", e.subcategory)
        assertEquals("Ozon", e.place)
        assertEquals("2026-11-14", e.date)
        assertTrue(e.note.orEmpty().contains("холодильник"))
    }

    @Test
    fun offlineTopUpForFridgeIsExpense() {
        val e = OfflineEntryParser.parse("пополнение за холодильник Озону плюс 5000", emptyList(), today).single()
        assertEquals(EntryType.EXPENSE, e.entryType)
        assertEquals(5000.0, e.amount, 0.0)
        assertEquals("Ozon", e.place)
    }

    @Test
    fun offlineSalaryAdvanceStaysIncome() {
        val e = OfflineEntryParser.parse("пришёл аванс 30 000", emptyList(), today).single()
        assertEquals(EntryType.INCOME, e.entryType)
        assertEquals("Аванс", e.source)
    }

    @Test
    fun offlineIncomeUnderstandsThousandsAndWords() {
        assertEquals(20000.0, OfflineIncomeParser.parse("премия 20 тысяч").single().amount, 0.0)
        assertEquals(80000.0, OfflineIncomeParser.parse("зарплата восемьдесят тысяч").single().amount, 0.0)
        assertEquals("зарплата", OfflineIncomeParser.parse("зарплата восемьдесят тысяч").single().note)
    }

    @Test
    fun promptExplainsPaymentsForPurchases() {
        val p = EntryPrompt.system(emptyList(), today, emptyList())
        assertTrue("Оплата покупки или услуги — всегда expense" in p)
        assertTrue("«пополнение за холодильник Озону плюс 5000» → expense 5000" in p)
    }
}
