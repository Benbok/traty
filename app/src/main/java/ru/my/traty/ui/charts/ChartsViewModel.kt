package ru.my.traty.ui.charts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.Places
import ru.my.traty.data.placeTotals
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.charts.model.CashFlowPoint
import ru.my.traty.ui.charts.model.CategoryCreditShare
import ru.my.traty.ui.charts.model.ChartExpenseCategory
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.charts.model.ChartSummary
import ru.my.traty.ui.charts.model.cumulativeCashFlowPoints
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ChartsUiState(
    val period: ChartPeriod = ChartPeriod.MONTH,
    /** Расчётный период вкладки «Месяц» (остальные вкладки — календарные месяцы). */
    val billingPeriod: BillingPeriod = BillingPeriod.containing(LocalDate.now(), 1),
    val periodStartDay: Int = 1,
    /** Границы текущего диапазона графиков (включительно). */
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val summary: ChartSummary = ChartSummary(0, 0, 0, null, 0, 0, 0),
    val cashFlowPoints: List<CashFlowPoint> = emptyList(),
    val expenseCategories: List<ChartExpenseCategory> = emptyList(),
    val cashFlowCategoryId: Long? = null,
    val categoryShares: List<CategoryCreditShare> = emptyList(),
    val recentIncomes: List<Income> = emptyList(),
)

data class ChartRange(val startMillis: Long, val endMillis: Long, val startDate: LocalDate, val endDate: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    val period = MutableStateFlow(ChartPeriod.MONTH)
    /** Границы произвольного периода «Даты» (включительно); по умолчанию — последние 30 дней. */
    private val customRange = MutableStateFlow(LocalDate.now().minusDays(29) to LocalDate.now())
    private val startDay = MutableStateFlow(1)
    val billingPeriod = MutableStateFlow(BillingPeriod.containing(LocalDate.now(), 1))

    private val cashFlowCategory = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            c.settings.flow.map { it.periodStartDay }.distinctUntilChanged().collect { d ->
                if (d != startDay.value) {
                    billingPeriod.value = BillingPeriod.containing(LocalDate.now(), d)
                    startDay.value = d
                }
            }
        }
    }

    private data class ChartSelection(
        val period: ChartPeriod, val billing: BillingPeriod, val startDay: Int, val range: ChartRange,
    )

    private val rangeFlow = combine(period, billingPeriod, customRange, startDay) { p, bp, custom, d ->
        val now = LocalDate.now()
        val range = when (p) {
            ChartPeriod.CUSTOM -> customChartRange(custom.first, custom.second, zone)
            ChartPeriod.MONTH -> {
                ChartRange(bp.startMillis(zone), bp.endMillisExclusive(zone), bp.startDate, bp.endDate)
            }
            ChartPeriod.QUARTER, ChartPeriod.HALF_YEAR, ChartPeriod.YEAR -> {
                // Окно из N расчётных периодов, последний из них — текущий (содержит сегодня).
                billingPeriodsRange(BillingPeriod.containing(now, d), p.billingPeriodCount()!!, d, zone)
            }
            ChartPeriod.ALL -> {
                val start = LocalDate.of(2020, 1, 1)
                val end = now.plusDays(1)
                ChartRange(0L, end.toStartMillis(), start, now)
            }
        }
        ChartSelection(p, bp, d, range)
    }

    val uiState: StateFlow<ChartsUiState> = rangeFlow.flatMapLatest { selection ->
        val range = selection.range
        combine(
            c.financialReports.expenses(range.startMillis, range.endMillis),
            c.financialReports.incomes(range.startMillis, range.endMillis),
            cashFlowCategory,
        ) { expenses, incomes, categoryId ->
            processUiState(selection.period, selection.billing, selection.startDay, range, expenses, incomes, categoryId)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChartsUiState())

    fun setCashFlowCategory(categoryId: Long?) {
        cashFlowCategory.value = categoryId
    }

    fun setPeriod(newPeriod: ChartPeriod) {
        period.value = newPeriod
    }

    /** Выбрать произвольный период «с даты по дату» и переключиться на него. */
    fun setCustomRange(from: LocalDate, to: LocalDate) {
        customRange.value = from to to
        period.value = ChartPeriod.CUSTOM
    }

    fun prevBillingPeriod() {
        billingPeriod.value = billingPeriod.value.previous(startDay.value)
    }

    fun nextBillingPeriod() {
        billingPeriod.value = billingPeriod.value.next(startDay.value)
    }

    private fun processUiState(
        p: ChartPeriod,
        bp: BillingPeriod,
        startDay: Int,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
        categoryId: Long?,
    ): ChartsUiState {
        val totalDebit = incomes.sumOf { it.amountKopecks }
        val totalCredit = expenses.sumOf { it.expense.amountKopecks }
        val netBalance = totalDebit - totalCredit
        val savingsRate = if (totalDebit > 0) {
            (((totalDebit - totalCredit).toDouble() / totalDebit) * 100).toInt()
        } else null

        val daysCount = (ChronoUnit.DAYS.between(range.startDate, range.endDate) + 1).coerceAtLeast(1L)
        val dailyAvg = totalCredit / daysCount

        val summary = ChartSummary(
            totalDebitKopecks = totalDebit,
            totalCreditKopecks = totalCredit,
            netBalanceKopecks = netBalance,
            savingsRatePercent = savingsRate,
            dailyAvgCreditKopecks = dailyAvg,
            incomeCount = incomes.size,
            expenseCount = expenses.size,
        )

        val expenseCategories = expenses.distinctBy { it.expense.categoryId }
            .map { ChartExpenseCategory(it.expense.categoryId, it.categoryName) }
            .sortedWith(compareBy<ChartExpenseCategory> { it.name.lowercase() }.thenBy { it.id })
        val selectedCategoryId = categoryId?.takeIf { id -> expenseCategories.any { it.id == id } }
        val chartExpenses = if (selectedCategoryId == null) expenses
            else expenses.filter { it.expense.categoryId == selectedCategoryId }
        val cashFlowPoints = calculateCashFlowPoints(p, startDay, range, chartExpenses, incomes)
        val categoryShares = calculateCategoryShares(expenses, totalCredit)

        return ChartsUiState(
            period = p,
            billingPeriod = bp,
            periodStartDay = startDay,
            rangeStart = range.startDate,
            rangeEnd = range.endDate,
            summary = summary,
            cashFlowPoints = cashFlowPoints,
            expenseCategories = expenseCategories,
            cashFlowCategoryId = selectedCategoryId,
            categoryShares = categoryShares,
            recentIncomes = incomes.take(15),
        )
    }

    private fun calculateCashFlowPoints(
        period: ChartPeriod,
        startDay: Int,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
    ): List<CashFlowPoint> {
        if (expenses.isEmpty() && incomes.isEmpty()) return emptyList()
        val incomeByDate = incomes.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, items) -> items.sumOf { it.amountKopecks } }
        val expenseByDate = expenses.groupBy {
            Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, items) -> items.sumOf { it.expense.amountKopecks } }
        val intervals = if (usesDailyPoints(period, range)) {
            generateSequence(range.startDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(range.endDate) }
                .map { it..it }.toList()
        } else {
            billingBuckets(range.startDate, range.endDate, startDay).map { it.startDate..it.endDate }
        }
        return cumulativeCashFlowPoints(intervals, incomeByDate, expenseByDate)
    }

    private fun calculateCategoryShares(
        expenses: List<ExpenseWithCategory>,
        totalCredit: Long,
    ): List<CategoryCreditShare> {
        if (totalCredit <= 0) return emptyList()
        val stores = Places.byCategory(expenses.placeTotals())
        return expenses.groupBy { it.expense.categoryId }
            .map { (catId, items) ->
                val sum = items.sumOf { it.expense.amountKopecks }
                val first = items.first()
                CategoryCreditShare(
                    categoryId = catId,
                    name = first.categoryName,
                    emoji = first.categoryEmoji,
                    totalKopecks = sum,
                    percent = sum.toFloat() / totalCredit,
                    stores = stores[catId].orEmpty(),
                )
            }
            .sortedByDescending { it.totalKopecks }
    }

    private fun LocalDate.toStartMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
}

