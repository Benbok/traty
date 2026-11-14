package ru.my.traty.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.YearMonth

class BudgetPlansTest {
    private val cafe = PlanKey(1)
    private val fuel = PlanKey(2, 20)
    private val nov = 202611
    private val dec = 202612
    private val jan = 202701

    private fun row(id: Long, key: PlanKey, month: Int, amount: Long, repeats: Boolean = true) =
        Budget(id = id, categoryId = key.categoryId, subcategoryId = key.subcategoryId, month = month, amountKopecks = amount, repeats = repeats)

    @Test
    fun monthKeyAndPeriodRespectStartDay() {
        assertEquals(202611, PlanMonth.of(YearMonth.of(2026, 11)))
        val p = PlanMonth.period(202611, 10)
        assertEquals(LocalDate.of(2026, 11, 10), p.startDate)
        assertEquals(LocalDate.of(2026, 12, 9), p.endDate)
        // Период 10 ноя — 9 дек относится к ноябрю, даже если сегодня 5 декабря
        assertEquals(202611, PlanMonth.of(BillingPeriod.containing(LocalDate.of(2026, 12, 5), 10)))
    }

    @Test
    fun repeatingPlanCarriesToLaterMonths() {
        val rows = listOf(row(1, cafe, nov, 5_000_00))
        assertEquals(5_000_00L, BudgetPlans.effective(rows, jan).single().amountKopecks)
        assertEquals(emptyList<Budget>(), BudgetPlans.effective(rows, 202610))
    }

    @Test
    fun oneOffPlanDoesNotRepeatAndDoesNotBreakInheritance() {
        val rows = listOf(row(1, cafe, nov, 5_000_00), row(2, cafe, dec, 8_000_00, repeats = false))
        assertEquals(8_000_00L, BudgetPlans.effective(rows, dec).single().amountKopecks)
        // В январе снова действует повторяющийся ноябрьский план
        assertEquals(5_000_00L, BudgetPlans.effective(rows, jan).single().amountKopecks)
    }

    @Test
    fun laterRepeatingPlanWins() {
        val rows = listOf(row(1, cafe, nov, 5_000_00), row(2, cafe, dec, 6_000_00), row(3, fuel, nov, 3_000_00))
        val jan = BudgetPlans.effective(rows, jan).associate { it.key to it.amountKopecks }
        assertEquals(mapOf(cafe to 6_000_00L, fuel to 3_000_00L), jan)
    }

    @Test
    fun setUpdatesOwnRowOrCreatesNew() {
        val rows = listOf(row(1, cafe, nov, 5_000_00))
        val same = BudgetPlans.set(rows, cafe, nov, 7_000_00, repeats = false)
        assertEquals(1L, same.id)
        assertEquals(false, same.repeats)
        val fresh = BudgetPlans.set(rows, cafe, dec, 7_000_00, repeats = true)
        assertEquals(0L, fresh.id)
        assertEquals(dec, fresh.month)
    }

    @Test
    fun removeFromStopsInheritedPlan() {
        val rows = listOf(row(1, cafe, nov, 5_000_00), row(2, cafe, jan, 6_000_00))
        val r = BudgetPlans.removeFrom(rows, cafe, dec)
        assertEquals(listOf(2L), r.delete.map { it.id })
        val after = rows - r.delete.toSet() + listOfNotNull(r.insert?.copy(id = 3))
        assertEquals(emptyList<Budget>(), BudgetPlans.effective(after, dec))
        assertEquals(emptyList<Budget>(), BudgetPlans.effective(after, jan))
        // Ноябрь не тронут
        assertEquals(5_000_00L, BudgetPlans.effective(after, nov).single().amountKopecks)
    }

    @Test
    fun removeFromWithoutInheritanceJustDeletes() {
        val rows = listOf(row(1, cafe, nov, 5_000_00))
        val r = BudgetPlans.removeFrom(rows, cafe, nov)
        assertEquals(listOf(1L), r.delete.map { it.id })
        assertNull(r.insert)
    }

    @Test
    fun categorySpendIncludesSubcategories() {
        val d = LocalDate.of(2026, 11, 3)
        val spent = BudgetPlans.spent(
            listOf(PlanSpend(2, 20, 1_000_00, d), PlanSpend(2, 21, 500_00, d), PlanSpend(2, null, 200_00, d)),
        )
        assertEquals(1_700_00L, spent[PlanKey(2)])
        assertEquals(1_000_00L, spent[fuel])
    }

    @Test
    fun hintIsAverageOfPastPeriodsMinusTenPercent() {
        val period = BillingPeriod.containing(LocalDate.of(2026, 11, 14), 1)
        val items = listOf(
            PlanSpend(1, null, 4_000_00, LocalDate.of(2026, 8, 5)),
            PlanSpend(1, null, 5_000_00, LocalDate.of(2026, 9, 5)),
            PlanSpend(1, null, 6_000_00, LocalDate.of(2026, 10, 5)),
            PlanSpend(1, null, 9_000_00, LocalDate.of(2026, 11, 5)), // текущий период не считается
        )
        // Окно: август, сентябрь, октябрь → (4 000 + 5 000 + 6 000) / 3 = 5 000 − 10 % = 4 500
        assertEquals(4_500_00L, BudgetPlans.hint(items, cafe, period, 1))
    }

    @Test
    fun hintIgnoresPeriodsBeforeHistoryStarts() {
        val period = BillingPeriod.containing(LocalDate.of(2026, 11, 14), 1)
        val items = listOf(PlanSpend(1, null, 3_000_00, LocalDate.of(2026, 10, 20)))
        // История только с октября: средняя за один период 3 000 − 10 % = 2 700
        assertEquals(2_700_00L, BudgetPlans.hint(items, cafe, period, 1))
        assertNull(BudgetPlans.hint(items, PlanKey(5), period, 1))
    }
}

class PlanProgressTest {
    private val period = BillingPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private fun plan(amount: Long) = Budget(id = 1, categoryId = 1, month = 202611, amountKopecks = amount)

    @Test
    fun perDayCountsTodayAndStatusOk() {
        // 10 ноября: прошло 10 дней, осталось 21 день с сегодняшним; потрачено 2 000 из 10 000
        val p = PlanProgresses.of(plan(10_000_00), 2_000_00, period, LocalDate.of(2026, 11, 10))
        assertEquals(PlanStatus.OK, p.status)
        assertEquals(8_000_00L / 21, p.perDay)
        assertEquals(6_000_00L, p.forecast)
    }

    @Test
    fun forecastAbovePlanIsAtRisk() {
        // 10 дней, 5 000 → к концу 15 000 при плане 10 000
        val p = PlanProgresses.of(plan(10_000_00), 5_000_00, period, LocalDate.of(2026, 11, 10))
        assertEquals(PlanStatus.AT_RISK, p.status)
    }

    @Test
    fun noForecastInFirstDays() {
        // Аренда в первый день: прогноз по темпу был бы ×30, поэтому первые дни его не считаем
        val p = PlanProgresses.of(plan(50_000_00), 45_000_00, period, LocalDate.of(2026, 11, 2))
        assertNull(p.forecast)
        assertEquals(PlanStatus.NEAR, p.status)
    }

    @Test
    fun overspendAndPastPeriod() {
        val over = PlanProgresses.of(plan(10_000_00), 12_000_00, period, LocalDate.of(2026, 12, 3))
        assertEquals(PlanStatus.OVER, over.status)
        assertEquals(-2_000_00L, over.remaining)
        assertNull(over.perDay)
        assertNull(over.forecast)
    }

    @Test
    fun allSortsWorstFirst() {
        val plans = listOf(plan(10_000_00), plan(1_000_00).copy(id = 2, categoryId = 2))
        val list = PlanProgresses.all(plans, mapOf(PlanKey(1) to 1_000_00L, PlanKey(2) to 2_000_00L), period, LocalDate.of(2026, 11, 10))
        assertEquals(listOf(2L, 1L), list.map { it.plan.categoryId })
    }
}
