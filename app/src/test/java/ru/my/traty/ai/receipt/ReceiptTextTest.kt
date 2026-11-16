package ru.my.traty.ai.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextTest {
    private val grocery = """
        ООО "Агроторг"
        Пятёрочка
        Кассовый чек. Приход
        Молоко 3,2% 1л        1 x 89.99
                                  =89.99
        Хлеб бородинский      2 x 45.00
                                  =90.00
        ПОДИТОГ                  179.99
        ИТОГО СКИДКА              10.00
        ИТОГ                    =169.99
        Безналичными             169.99
        Кассир: Иванова Мария
        ИНН 7825706086
        ФН 9999078900012345
        ФД 12345 ФП 1234567890
        Эл. адр. покупателя: me@example.com
    """.trimIndent()

    @Test fun printedTotal_takesItogLine_notSubtotalOrDiscount() {
        assertEquals(16999L, ReceiptText.printedTotal(grocery))
    }

    @Test fun printedTotal_amountOnNextLine() {
        assertEquals(123456L, ReceiptText.printedTotal("Товар 1234.56\nИТОГО К ОПЛАТЕ\n1 234,56"))
    }

    @Test fun printedTotal_kOplate_withThousands() {
        assertEquals(4_532_000L, ReceiptText.printedTotal("Ламинат 45 320,00\nК ОПЛАТЕ: 45 320,00"))
    }

    @Test fun printedTotal_wholeRubles() {
        assertEquals(35000L, ReceiptText.printedTotal("Капучино 350\nИтого 350"))
    }

    @Test fun printedTotal_missing() {
        assertNull(ReceiptText.printedTotal("Молоко 89.99\nХлеб 45"))
    }

    @Test fun forAi_dropsServiceLinesAndPersonalData() {
        val text = ReceiptText.forAi(grocery)
        assertTrue(text.contains("Молоко"))
        assertTrue(text.contains("ИТОГ"))
        assertFalse(text.contains("Иванова"))
        assertFalse(text.contains("7825706086"))
        assertFalse(text.contains("9999078900012345"))
        assertFalse(text.contains("me@example.com"))
        assertFalse(text.contains("\n\n"))
    }

    @Test fun forAi_isCut() {
        assertEquals(ReceiptText.MAX_CHARS, ReceiptText.forAi("а".repeat(ReceiptText.MAX_CHARS * 2)).length)
    }

    @Test fun lastAmount_formats() {
        assertEquals(8999L, ReceiptText.lastAmount("1 x 89.99"))
        assertEquals(150L, ReceiptText.lastAmount("=1,5"))
        assertEquals(100000L, ReceiptText.lastAmount("1 000"))
    }
}
