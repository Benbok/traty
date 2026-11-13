package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.IncomeOrigin
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IncomeDraftTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 13)
    private val nbsp: Char = 0x00A0.toChar()
    private val nnbsp: Char = 0x202F.toChar()

    private fun item(amount: String, source: String = "Зарплата", note: String = "", date: String = today.toString()) =
        IncomeDraftItem(amount, source, note, date)

    private fun kopecks(amount: String): Long? =
        item(amount).toIncome(IncomeOrigin.TEXT, zone, today)?.amountKopecks

    @Test
    fun toIncomeParsesCommaAndSpaces() {
        assertEquals(8_000_050L, kopecks("80 000,50"))
        assertEquals(8_000_050L, kopecks("80${nbsp}000,50"))
        assertEquals(8_000_050L, kopecks("80${nnbsp}000,50"))
        assertEquals(150L, kopecks("1,5"))
        assertEquals(3_000_000L, kopecks("30000"))
    }

    @Test
    fun toIncomeRejectsNonPositiveOrGarbage() {
        assertNull(item("0").toIncome(IncomeOrigin.TEXT, zone, today))
        assertNull(item("abc").toIncome(IncomeOrigin.TEXT, zone, today))
        assertNull(item("").toIncome(IncomeOrigin.TEXT, zone, today))
        assertNull(item("-5").toIncome(IncomeOrigin.TEXT, zone, today))
    }

    @Test
    fun toIncomeUsesNoonForPastDate() {
        val income = item("100", date = "2026-11-10").toIncome(IncomeOrigin.TEXT, zone, today)
        assertNotNull(income)
        val local = java.time.Instant.ofEpochMilli(income!!.timestamp).atZone(zone)
        assertEquals(LocalDate.of(2026, 11, 10), local.toLocalDate())
        assertEquals(LocalTime.NOON, local.toLocalTime())
    }

    @Test
    fun toIncomeKeepsOriginAndSource() {
        val income = item("500", source = "Аванс", note = " бонус ").toIncome(IncomeOrigin.VOICE, zone, today)!!
        assertEquals(IncomeOrigin.VOICE, income.origin)
        assertEquals("Аванс", income.source)
        assertEquals("бонус", income.note)
        assertEquals("RUB", income.currency)
    }
}
