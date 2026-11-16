package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry

class AmountCheckTest {
    private fun e(amount: Double) = ParsedEntry(type = "expense", amount = amount)
    private fun bad(phrase: String, vararg amounts: Double) = AmountCheck.mismatches(amounts.map(::e), phrase).map { it.amount }

    @Test
    fun amountNamedInPhrasePasses() {
        assertEquals(emptyList<Double>(), bad("кофе 250", 250.0))
        assertEquals(emptyList<Double>(), bad("кофе двести пятьдесят", 250.0))
        assertEquals(emptyList<Double>(), bad("такси полторы тысячи", 1500.0))
        assertEquals(emptyList<Double>(), bad("пятёрочка пятьсот и триста", 500.0, 300.0))
        assertEquals(emptyList<Double>(), bad("зоомагазин вчера 731 лекарство для кошки Чуни от блох", 731.0))
    }

    @Test
    fun formsTheSimpleParserMissesAreStillRecognized() {
        assertEquals(emptyList<Double>(), bad("две с половиной тысячи коммуналка", 2500.0))
        assertEquals(emptyList<Double>(), bad("машина миллион двести", 1_000_000.0))
        assertEquals(emptyList<Double>(), bad("полтора миллиона на квартиру", 1_500_000.0))
        assertEquals(emptyList<Double>(), bad("две сотни на кофе", 200.0))
        assertEquals(listOf(3000.0), bad("две с половиной тысячи коммуналка", 3000.0))
    }

    @Test
    fun amountThatPhraseDoesNotContainIsFlagged() {
        assertEquals(listOf(5000.0), bad("купил 2 билета за 3000", 5000.0))
        assertEquals(listOf(2500.0), bad("кофе 250", 2500.0))
        assertEquals(listOf(731.0), bad("зоомагазин 713", 731.0))
    }

    @Test
    fun phraseWithoutAnyNumberCannotConfirmAmount() {
        assertEquals(listOf(500.0), bad("потратил на еду как обычно", 500.0))
    }

    @Test
    fun sumOfNamedPricesPasses() {
        // Список с ценами без итога: приложение складывает цены само
        assertEquals(emptyList<Double>(), bad("рыба 500 хлеб 80 сок 120", 700.0))
        assertEquals(emptyList<Double>(), bad("рыба 500 хлеб 80 сок 120", 580.0))
        assertEquals(listOf(999.0), bad("рыба 500 хлеб 80 сок 120", 999.0))
    }

    @Test
    fun entryWithoutPaymentTodayIsNotChecked() {
        // «взял в кредит»: сегодня ничего не платили, цена лежит в part.total
        assertEquals(emptyList<Double>(), bad("взял ноутбук в кредит за восемьдесят тысяч", 0.0))
    }

    @Test
    fun noticeNamesBothAmounts() {
        val list = AmountCheck.mismatches(listOf(e(2500.0)), "кофе 250")
        val text = AmountCheck.notice(list, "кофе 250")!!
        assertTrue(text, Regex("2[\\u00A0 ]500 ₽").containsMatchIn(text) && "250 ₽" in text)
        assertNull(AmountCheck.notice(emptyList(), "кофе 250"))
    }
}
