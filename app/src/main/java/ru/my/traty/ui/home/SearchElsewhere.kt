package ru.my.traty.ui.home

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.period.BillingPeriod
import java.time.Instant
import java.time.ZoneId

/** Найденное за один расчётный период: траты и доходы, новые сверху. */
data class PeriodMatches(
    val period: BillingPeriod,
    val expenses: List<ExpenseWithCategory>,
    val incomes: List<Income>,
) {
    val expenseTotal: Long get() = expenses.sumOf { it.expense.amountKopecks }
    val count: Int get() = expenses.size + incomes.size
}

/**
 * Поиск за пределами открытого периода: «холодильник» мог быть куплен полгода назад.
 * Возвращает найденное по остальным периодам, от новых к старым; открытый период [current] пропускается,
 * его записи и так видны в ленте.
 */
object SearchElsewhere {

    fun find(
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
        query: String,
        current: BillingPeriod,
        startDay: Int,
        zone: ZoneId,
    ): List<PeriodMatches> {
        if (query.isBlank()) return emptyList()
        fun periodOf(ts: Long) = BillingPeriod.containing(Instant.ofEpochMilli(ts).atZone(zone).toLocalDate(), startDay)
        val foundExpenses = expenses.filter { FeedSearch.matches(it, query) }
            .groupBy { periodOf(it.expense.timestamp) }
        val foundIncomes = incomes.filter { FeedSearch.matches(it, query) }
            .groupBy { periodOf(it.timestamp) }
        return (foundExpenses.keys + foundIncomes.keys)
            .filter { it != current }
            .sortedByDescending { it.startDate }
            .map { p ->
                PeriodMatches(
                    period = p,
                    expenses = foundExpenses[p].orEmpty().sortedByDescending { it.expense.timestamp },
                    incomes = foundIncomes[p].orEmpty().sortedByDescending { it.timestamp },
                )
            }
    }
}
