package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Test

class DebtTextsTest {
    private val fridge = Debt(title = "Холодильник", categoryId = 1)
    private val laptop = Debt(title = "Ноутбук", categoryId = 1, kind = DebtKind.LOAN.key)

    @Test
    fun notes() {
        assertEquals("Холодильник · аванс", DebtTexts.paymentNote(fridge, PaymentPart.PRINCIPAL, null, null, first = true))
        assertEquals("Холодильник · доплата", DebtTexts.paymentNote(fridge, PaymentPart.PRINCIPAL, null, null, first = false))
        assertEquals("Ноутбук · первый платёж", DebtTexts.paymentNote(laptop, PaymentPart.PRINCIPAL, null, null, first = true))
        assertEquals("Ноутбук · платёж 2 из 12", DebtTexts.paymentNote(laptop, PaymentPart.PRINCIPAL, 2, 12, first = false))
        assertEquals("Ноутбук · проценты, платёж 2 из 12", DebtTexts.paymentNote(laptop, PaymentPart.INTEREST, 2, 12, first = false))
    }
}
