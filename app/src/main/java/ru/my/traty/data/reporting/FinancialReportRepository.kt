package ru.my.traty.data.reporting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.purchases.PurchaseGoalStatus

class FinancialReportRepository(private val db: AppDatabase) {
    private val resolvedGoals = db.purchaseGoals().observeResolved()
    private val cancelledExpenses = resolvedGoals.map { goals ->
        goals.filter { it.goal.actualPriceKopecks == null }
            .flatMap { it.contributions }
            .mapTo(mutableSetOf()) { it.id }
    }.distinctUntilChanged()

    fun expenses(from: Long, to: Long) = combine(
        db.expenses().observeRange(from, to),
        cancelledExpenses,
    ) { rows, excluded ->
        rows.filterNot { it.expense.id in excluded }
    }.flowOn(Dispatchers.Default)

    fun incomes(from: Long, to: Long) = combine(db.incomes().observeAll(), resolvedGoals) { rows, goals ->
        val excluded = cancelledRefundIds(rows, goals)
        rows.asReversed().filter { it.timestamp >= from && it.timestamp < to && it.id !in excluded }
    }.flowOn(Dispatchers.Default)
}

internal fun reportTotals(rows: List<ExpenseWithCategory>): List<CategoryTotal> =
    rows.groupBy { it.expense.categoryId }.map { (categoryId, entries) ->
        val first = entries.first()
        CategoryTotal(categoryId, first.categoryName, first.categoryEmoji, entries.sumOf { it.expense.amountKopecks })
    }.sortedByDescending { it.total }

/** Старые копии хранят возвраты по отмене и по окончательной цене с одним признаком. */
internal fun cancelledRefundIds(incomes: List<Income>, goals: List<PurchaseGoalStatus>): Set<Long> {
    val expected = goals.mapNotNull { status ->
        val lastContribution = status.contributions.maxOfOrNull { it.timestamp } ?: return@mapNotNull null
        val price = status.goal.actualPriceKopecks
        val amount = status.savedKopecks - (price ?: 0L)
        if (amount <= 0L) return@mapNotNull null
        ExpectedRefund(status.goal.title, amount, lastContribution, cancelled = price == null)
    }.groupBy { it.title to it.amount }.mapValues { (_, matches) ->
        matches.sortedByDescending { it.lastContribution }.toMutableList()
    }

    val refunds = incomes.filter {
        it.origin == IncomeOrigin.PURCHASE_REFUND || it.origin == IncomeOrigin.PURCHASE_CANCELLATION_REFUND
    }.sortedWith(compareByDescending<Income> { it.timestamp }.thenByDescending { it.id })

    return buildSet {
        for (refund in refunds) {
            val explicit = refund.origin == IncomeOrigin.PURCHASE_CANCELLATION_REFUND
            if (explicit) add(refund.id)
            val candidates = expected[refund.note to refund.amountKopecks] ?: continue
            val index = candidates.indexOfFirst {
                it.lastContribution <= refund.timestamp && (!explicit || it.cancelled)
            }
            if (index >= 0 && candidates.removeAt(index).cancelled) add(refund.id)
        }
    }
}

private data class ExpectedRefund(val title: String, val amount: Long, val lastContribution: Long, val cancelled: Boolean)
