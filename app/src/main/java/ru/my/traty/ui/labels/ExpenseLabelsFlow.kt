package ru.my.traty.ui.labels

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.my.traty.AppContainer
import ru.my.traty.data.budget.OverrunSpend
import ru.my.traty.data.budget.PlanOverruns
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.data.labels.ExpenseLabeler
import ru.my.traty.data.recurring.RecurringRepository
import java.time.Instant
import java.time.ZoneId

/**
 * Метки на тратах (🔺 🔁 🆕 ⚡ 📅) по всей истории, не только по показанному периоду.
 * ⚡ учитывает и план трат: трата сверх плана в импульсивной категории тоже импульсивная.
 */
fun expenseLabelsFlow(c: AppContainer, zone: ZoneId): Flow<Map<Long, List<ExpenseLabelHit>>> {
    val plans = combine(
        c.budgets.observeAll(),
        c.budgets.observeTotals(),
        c.settings.flow.map { it.periodStartDay }.distinctUntilChanged(),
    ) { rows, totals, startDay -> Triple(rows, totals, startDay) }
    // Платежи по покупкам частями не предлагаются подписками
    val expenses = combine(c.db.expenses().observeAllWithCategory(), c.debts.observePayments()) { list, payments ->
        list to payments.map { it.expenseId }.toSet()
    }
    return combine(
        expenses,
        c.db.recurring().observeAll(),
        c.categories.observeAllWithSubs(),
        c.settings.flow.map { it.labelsEnabled }.distinctUntilChanged(),
        plans,
    ) { (expenses, debtExpenseIds), recurring, cats, enabled, (rows, totals, startDay) ->
        if (!enabled) return@combine emptyMap()
        val candidates = RecurringRepository.suggest(expenses, recurring, cats, debtExpenseIds = debtExpenseIds)
        val overPlan = PlanOverruns.find(
            expenses.map {
                val e = it.expense
                OverrunSpend(
                    e.id, e.categoryId, e.subcategoryId, e.amountKopecks,
                    Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate(), e.timestamp,
                )
            },
            rows, totals, startDay,
        )
        ExpenseLabeler.label(
            expenses,
            ExpenseLabeler.subscriptionExpenseIds(expenses, recurring, candidates),
            zone,
            impulsiveCategories = cats.filter { it.category.isImpulsive }.map { it.category.name },
            overPlan = overPlan,
        )
    }
}
