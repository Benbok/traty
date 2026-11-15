package ru.my.traty.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import ru.my.traty.data.period.BillingPeriod
import org.junit.Test
import java.time.LocalDate

class TotalBudgetsTest {
    private val nov = 202611
    private val dec = 202612
    private val jan = 202701

    private fun row(id: Long, month: Int, amount: Long, repeats: Boolean = true) =
        MonthBudget(id = id, month = month, amountKopecks = amount, repeats = repeats)

    @Test
    fun repeatingLimitCarriesForwardAndOneOffDoesNot() {
        val rows = listOf(row(1, nov, 80_000_00), row(2, dec, 100_000_00, repeats = false))
        assertEquals(80_000_00L, TotalBudgets.effective(rows, nov)?.amountKopecks)
        assertEquals(100_000_00L, TotalBudgets.effective(rows, dec)?.amountKopecks)
        assertEquals(80_000_00L, TotalBudgets.effective(rows, jan)?.amountKopecks)
        assertNull(TotalBudgets.effective(rows, 202610))
    }

    @Test
    fun setReusesRowOfSameMonth() {
        val rows = listOf(row(1, nov, 80_000_00))
        assertEquals(1L, TotalBudgets.set(rows, nov, 90_000_00, repeats = true).id)
        assertEquals(0L, TotalBudgets.set(rows, dec, 90_000_00, repeats = true).id)
    }

    @Test
    fun removeFromStopsInheritedLimit() {
        val rows = listOf(row(1, nov, 80_000_00), row(2, jan, 70_000_00))
        val r = TotalBudgets.removeFrom(rows, dec)
        assertEquals(listOf(2L), r.delete.map { it.id })
        val after = rows - r.delete.toSet() + listOfNotNull(r.insert?.copy(id = 3))
        assertNull(TotalBudgets.effective(after, dec))
        assertNull(TotalBudgets.effective(after, jan))
        assertEquals(80_000_00L, TotalBudgets.effective(after, nov)?.amountKopecks)
        // Без повтора от прошлых месяцев строка «без лимита» не нужна
        assertNull(TotalBudgets.removeFrom(listOf(row(1, nov, 1)), nov).insert)
    }

    @Test
    fun progressUsesSamePaceRulesAsCategoryPlans() {
        val period = BillingPeriod.containing(LocalDate.of(2026, 11, 10), 1)
        val p = TotalProgress.of(row(1, nov, 30_000_00), 25_000_00, period, LocalDate.of(2026, 11, 10))
        // 10-й день из 30, потрачено 25 000 — по темпу будет 75 000 при лимите 30 000
        assertEquals(PlanStatus.AT_RISK, p.status)
        assertEquals(5_000_00L, p.remaining)
        // 21 день с сегодняшним: 5 000 / 21
        assertEquals(5_000_00L / 21, p.perDay)
        val over = TotalProgress.of(row(1, nov, 30_000_00), 31_000_00, period, LocalDate.of(2026, 11, 10))
        assertEquals(PlanStatus.OVER, over.status)
    }
}
