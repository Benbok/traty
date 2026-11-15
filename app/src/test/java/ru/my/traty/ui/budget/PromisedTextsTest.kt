package ru.my.traty.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.budget.Promised
import ru.my.traty.data.budget.PromisedItem
import ru.my.traty.ui.formatRub
import java.time.LocalDate

class PromisedTextsTest {
    private val d = LocalDate.of(2026, 11, 20)
    private fun item(rub: Long, approx: Boolean = false) = PromisedItem("Телефон", d, rub * 100, fromDebt = true, approximate = approx)

    @Test
    fun summaryCountsPayments() {
        val one = promisedSummary(Promised(listOf(item(10_000))))
        assertEquals("Уже обещано до конца периода: ${formatRub(1_000_000)} · 1 платёж", one)
        val two = promisedSummary(Promised(listOf(item(10_000), item(500))))
        assertTrue(two.endsWith("· 2 платежа"))
    }

    @Test
    fun approximateSaysAbout() {
        assertTrue(promisedSummary(Promised(listOf(item(1_200, approx = true)))).contains("около"))
    }

    @Test
    fun freeAfterPromised() {
        assertEquals("С учётом обещанного свободно ${formatRub(500_000)}", promisedFreeText(500_000, false))
        assertEquals("С учётом обещанного не хватит ${formatRub(200_000)}", promisedFreeText(-200_000, false))
        assertTrue(promisedFreeText(-1, true).contains("около"))
    }
}
