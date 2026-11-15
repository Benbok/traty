package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchaseTermsTest {
    private fun find(text: String) = checkNotNull(PurchaseTermsParser.find(text)) { text }

    @Test
    fun advanceForFridge() {
        val t = find("аванс за холодильник, купленный на Озоне вчера, 20 тысяч")
        assertEquals(PurchaseTerms.PARTIAL, t.normalizedKind)
        assertNull(t.total)
    }

    @Test
    fun topUp() {
        assertEquals(PurchaseTerms.TOPUP, find("пополнение за холодильник Озону плюс 5000").normalizedKind)
        assertEquals(PurchaseTerms.TOPUP, find("доплатил за холодильник 5000").normalizedKind)
    }

    @Test
    fun installmentWithCountAndTotal() {
        val t = find("взял телевизор в рассрочку на 6 месяцев, всего 60 тысяч, первый платёж 10 тысяч")
        assertEquals(PurchaseTerms.INSTALLMENT, t.normalizedKind)
        assertEquals(6, t.count)
        assertEquals("month", t.period)
        assertEquals(60000.0, checkNotNull(t.total), 0.0)
    }

    @Test
    fun countInWordsAndYears() {
        assertEquals(6, find("рассрочка на шесть месяцев").count)
        assertEquals(12, find("в кредит на год").count)
        assertEquals(24, find("в кредит на 2 года").count)
        assertEquals(6, find("рассрочка на полгода").count)
    }

    @Test
    fun loanRate() {
        val t = find("купил ноутбук в кредит 80 тысяч под 20 процентов на год")
        assertEquals(PurchaseTerms.LOAN, t.normalizedKind)
        assertEquals(20.0, checkNotNull(t.rate), 0.0)
        assertEquals(12, t.count)
        assertEquals(17.9, checkNotNull(find("кредит под 17,9% на 12 месяцев").rate), 0.0)
        assertEquals(20.0, checkNotNull(find("в кредит под двадцать процентов").rate), 0.0)
    }

    @Test
    fun bnplDefaults() {
        val t = find("купил кроссовки Долями 12000")
        assertEquals(PurchaseTerms.INSTALLMENT, t.normalizedKind)
        assertEquals(4, t.count)
        assertEquals("2weeks", t.period)
    }

    @Test
    fun ordinaryPhrasesHaveNoTerms() {
        assertNull(PurchaseTermsParser.find("магнит 250 молоко"))
        assertNull(PurchaseTermsParser.find("пришёл аванс 30000"))
        assertNull(PurchaseTermsParser.find("аптека плюс 300"))
    }

    @Test
    fun aiPartIsDecodedAndUnpaidLoanIsKept() {
        val raw = """{"items":[{"type":"expense","amount":0,"category":"Дом и быт","note":"ноутбук","part":{"kind":"loan","total":80000,"count":12,"rate":20}}]}"""
        val e = EntryParsing.decode(raw).single()
        assertEquals(PurchaseTerms.LOAN, e.part?.normalizedKind)
        assertEquals(12, e.part?.count)
        assertTrue(EntryParsing.decode("""{"items":[{"type":"expense","amount":0,"category":"Прочее"}]}""").isEmpty())
    }

    @Test
    fun promptAsksForPartWithoutCalculations() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 15), emptyList())
        assertTrue("part заполняй только для покупки частями" in p)
        assertTrue("Ничего не вычисляй" in p)
    }

    @Test
    fun offlineParserAddsTerms() {
        val e = OfflineEntryParser.parse("аванс за холодильник на Озоне 20 тысяч", emptyList(), LocalDate.of(2026, 11, 15)).single()
        assertEquals(PurchaseTerms.PARTIAL, e.part?.normalizedKind)
    }
}
