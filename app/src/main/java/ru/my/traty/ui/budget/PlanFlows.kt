package ru.my.traty.ui.budget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.my.traty.AppContainer
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.budget.PlanMonth
import ru.my.traty.data.budget.TotalBudgets
import ru.my.traty.data.budget.TotalProgress
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.ZoneId

/** Планы показанного периода с прогрессом — для Статистики и Трат. */
fun planItemsFlow(
    c: AppContainer,
    period: Flow<BillingPeriod>,
    expenses: Flow<List<ExpenseWithCategory>>,
    categories: Flow<List<CategoryWithSubs>>,
    zone: ZoneId,
): Flow<List<PlanItem>> = combine(c.budgets.observeAll(), period, expenses, categories) { rows, p, list, cats ->
    planItems(rows, p, list, cats, zone)
}

/** Общий лимит показанного периода; null — лимит не задан. */
fun totalLimitFlow(
    c: AppContainer,
    period: Flow<BillingPeriod>,
    expenses: Flow<List<ExpenseWithCategory>>,
): Flow<TotalProgress?> = combine(c.budgets.observeTotals(), period, expenses) { rows, p, list -> totalProgress(rows, p, list) }

/** Общий лимит периода [period] и все его траты [periodExpenses]. */
fun totalProgress(
    rows: List<MonthBudget>,
    period: BillingPeriod,
    periodExpenses: List<ExpenseWithCategory>,
    today: LocalDate = LocalDate.now(),
): TotalProgress? {
    val limit = TotalBudgets.effective(rows, PlanMonth.of(period)) ?: return null
    return TotalProgress.of(limit, periodExpenses.sumOf { it.expense.amountKopecks }, period, today)
}
