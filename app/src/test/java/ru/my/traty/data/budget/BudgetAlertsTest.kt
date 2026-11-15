package ru.my.traty.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetAlertsTest {
    private val nov = 202611
    private fun t(spent: Long, planned: Long = 10_000_00, id: String = "c1") = AlertTarget(id, "Кафе", planned, spent)

    @Test
    fun nothingBelowEightyPercent() {
        val due = BudgetAlerts.due(nov, listOf(t(7_999_00)), emptySet())
        assertTrue(due.alerts.isEmpty())
        assertTrue(due.alsoMark.isEmpty())
    }

    @Test
    fun eightyPercentOnceThenHundred() {
        val first = BudgetAlerts.due(nov, listOf(t(8_500_00)), emptySet()).alerts.single()
        assertEquals("Кафе: потрачено 85 % плана", first.title)
        assertTrue(first.text.startsWith("Осталось 1"))
        val sent = setOf(first.key)
        assertTrue(BudgetAlerts.due(nov, listOf(t(9_000_00)), sent).alerts.isEmpty())
        val over = BudgetAlerts.due(nov, listOf(t(10_400_00)), sent).alerts.single()
        assertEquals("Кафе: план превышен", over.title)
        assertTrue(over.text.contains("перерасход 400"))
    }

    @Test
    fun jumpOverHundredGivesOneAlertAndMarksEighty() {
        val due = BudgetAlerts.due(nov, listOf(t(12_000_00)), emptySet())
        assertEquals(1, due.alerts.size)
        assertEquals(setOf(BudgetAlerts.key(nov, "c1", 80)), due.alsoMark)
        val sent = due.alsoMark + due.alerts.map { it.key }
        // Траты уменьшились и снова выросли — повторов в этом месяце нет
        assertTrue(BudgetAlerts.due(nov, listOf(t(8_100_00)), sent).alerts.isEmpty())
        // В следующем месяце пороги снова работают
        assertEquals(1, BudgetAlerts.due(202612, listOf(t(8_100_00)), BudgetAlerts.keepForMonth(sent, 202612)).alerts.size)
    }

    @Test
    fun exactlyPlannedIsFull() {
        assertEquals("Кафе: план выбран", BudgetAlerts.due(nov, listOf(t(10_000_00)), emptySet()).alerts.single().title)
    }

    @Test
    fun targetsIncludeSubcategoryAndTotalAndSkipArchived() {
        val budgets = listOf(
            Budget(id = 1, categoryId = 1, month = nov, amountKopecks = 5_000_00),
            Budget(id = 2, categoryId = 1, subcategoryId = 10, month = nov, amountKopecks = 1_000_00),
            Budget(id = 3, categoryId = 2, month = nov, amountKopecks = 1_000_00),
        )
        val totals = listOf(MonthBudget(id = 1, month = nov, amountKopecks = 50_000_00))
        val d = LocalDate.of(2026, 11, 5)
        val spends = listOf(PlanSpend(1, 10, 900_00, d), PlanSpend(1, null, 100_00, d), PlanSpend(3, null, 2_000_00, d))
        val targets = BudgetAlerts.targets(budgets, totals, nov, spends) { k ->
            when {
                k.categoryId == 2L -> null
                k.subcategoryId == 10L -> "Кафе · Кофе"
                else -> "Кафе"
            }
        }.associateBy { it.id }
        assertEquals(setOf("c1", "c1s10", "total"), targets.keys)
        assertEquals(1_000_00L, targets.getValue("c1").spent)
        assertEquals(900_00L, targets.getValue("c1s10").spent)
        assertEquals(3_000_00L, targets.getValue("total").spent)
    }
}
