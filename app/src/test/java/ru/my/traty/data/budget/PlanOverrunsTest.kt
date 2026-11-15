package ru.my.traty.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PlanOverrunsTest {
    private fun s(id: Long, cat: Long, amount: Long, day: LocalDate, sub: Long? = null) =
        OverrunSpend(id, cat, sub, amount, day, day.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000 + id)

    private val plan = listOf(Budget(id = 1, categoryId = 1, month = 202611, amountKopecks = 3_000_00))

    @Test
    fun marksSpendThatCrossesPlanAndLaterOnes() {
        val spends = listOf(
            s(1, 1, 2_000_00, LocalDate.of(2026, 11, 3)),
            s(2, 1, 1_500_00, LocalDate.of(2026, 11, 5)),
            s(3, 1, 200_00, LocalDate.of(2026, 11, 6)),
            s(4, 2, 50_000_00, LocalDate.of(2026, 11, 6)),
        )
        val found = PlanOverruns.find(spends, plan, emptyList(), startDay = 1)
        assertEquals(setOf(2L, 3L), found.keys)
        val text = found.getValue(2).replace('\u00A0', ' ').replace('\u202F', ' ')
        assertTrue(text, text.contains("3 500 ₽ из 3 000 ₽"))
    }

    @Test
    fun orderInsideMonthIsByTimeNotInput() {
        val spends = listOf(s(2, 1, 1_500_00, LocalDate.of(2026, 11, 5)), s(1, 1, 2_000_00, LocalDate.of(2026, 11, 3)))
        assertEquals(setOf(2L), PlanOverruns.find(spends, plan, emptyList(), 1).keys)
    }

    @Test
    fun eachPeriodCountsSeparatelyAndRespectsStartDay() {
        // Период с 10-го: 5 ноября относится к октябрьскому периоду, где плана нет
        val spends = listOf(s(1, 1, 5_000_00, LocalDate.of(2026, 11, 5)), s(2, 1, 1_000_00, LocalDate.of(2026, 11, 12)))
        assertTrue(PlanOverruns.find(spends, plan, emptyList(), startDay = 10).isEmpty())
    }

    @Test
    fun totalLimitCountsAllCategories() {
        val totals = listOf(MonthBudget(id = 1, month = 202611, amountKopecks = 10_000_00))
        val spends = listOf(s(1, 5, 6_000_00, LocalDate.of(2026, 11, 2)), s(2, 6, 5_000_00, LocalDate.of(2026, 11, 3)))
        val found = PlanOverruns.find(spends, emptyList(), totals, 1)
        assertEquals(setOf(2L), found.keys)
        assertTrue(found.getValue(2).contains("общего лимита"))
    }

    @Test
    fun subcategoryPlanIsChecked() {
        val sub = listOf(Budget(id = 1, categoryId = 1, subcategoryId = 7, month = 202611, amountKopecks = 1_000_00))
        val spends = listOf(s(1, 1, 900_00, LocalDate.of(2026, 11, 2), sub = 7), s(2, 1, 900_00, LocalDate.of(2026, 11, 3)), s(3, 1, 200_00, LocalDate.of(2026, 11, 4), sub = 7))
        assertEquals(setOf(3L), PlanOverruns.find(spends, sub, emptyList(), 1).keys)
    }
}
