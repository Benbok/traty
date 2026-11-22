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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.Places
import ru.my.traty.data.placeTotals
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.charts.model.CashFlowCategory
import ru.my.traty.ui.charts.model.CashFlowCategoryKey
import ru.my.traty.ui.charts.model.CashFlowKind
import ru.my.traty.ui.charts.model.MAX_COMPARED_CATEGORIES
import ru.my.traty.ui.charts.model.cashFlowGranularity
import ru.my.traty.ui.charts.model.cashFlowIntervals
import ru.my.traty.ui.charts.model.CashFlowGraph
import ru.my.traty.ui.charts.model.buildCashFlowSelection
import ru.my.traty.ui.charts.model.CategoryCreditShare
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.charts.model.ChartSummary
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
    val cashFlowKind: CashFlowKind = CashFlowKind.EXPENSE,
    val cashFlow: CashFlowGraph = CashFlowGraph(),
    val cashFlowCategories: List<CashFlowCategory> = emptyList(),
    val cashFlowCategoryKeys: Set<CashFlowCategoryKey> = emptySet(),
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

    private data class CashFlowFilter(
        val kind: CashFlowKind = CashFlowKind.EXPENSE,
        val selections: Map<CashFlowKind, List<CashFlowCategory>> = emptyMap(),
    )

    private val cashFlowFilter = MutableStateFlow(CashFlowFilter())

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
            cashFlowFilter,
        ) { expenses, incomes, filter ->
            processUiState(selection.period, selection.billing, selection.startDay, range, expenses, incomes, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChartsUiState())

    fun setCashFlowKind(kind: CashFlowKind) {
        cashFlowFilter.update { it.copy(kind = kind) }
    }

    fun toggleCashFlowCategory(key: CashFlowCategoryKey) {
        val state = uiState.value
        val category = state.cashFlowCategories.firstOrNull { it.key == key } ?: return
        cashFlowFilter.update { filter ->
            if (filter.kind != state.cashFlowKind) return@update filter
            val selected = filter.selections[filter.kind].orEmpty()
            val next = when {
                selected.any { it.key == key } -> selected.filterNot { it.key == key }
                selected.size < MAX_COMPARED_CATEGORIES -> selected + category
                else -> return@update filter
            }
            filter.copy(selections = filter.selections + (filter.kind to next))
        }
    }

    fun clearCashFlowCategories() {
        cashFlowFilter.update { it.copy(selections = it.selections - it.kind) }
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
        filter: CashFlowFilter,
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

        val granularity = cashFlowGranularity(range.startDate, range.endDate)
        val timestamps = when (filter.kind) {
            CashFlowKind.EXPENSE -> expenses.asSequence().map { it.expense.timestamp }
            CashFlowKind.INCOME -> incomes.asSequence().map { it.timestamp }
        }
        val graphStart = if (p == ChartPeriod.ALL) {
            timestamps.minOrNull()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: range.startDate
        } else range.startDate
        val lastRecordedDate = timestamps.maxOrNull()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val graphEnd = minOf(range.endDate, maxOf(LocalDate.now(zone), lastRecordedDate ?: graphStart))
        val cashFlow = buildCashFlowSelection(
            cashFlowIntervals(graphStart, graphEnd, startDay, granularity),
            granularity, expenses, incomes, filter.kind, filter.selections[filter.kind].orEmpty(), zone,
        )
        val categoryShares = calculateCategoryShares(expenses, totalCredit)

        return ChartsUiState(
            period = p,
            billingPeriod = bp,
            periodStartDay = startDay,
            rangeStart = range.startDate,
            rangeEnd = range.endDate,
            summary = summary,
            cashFlowKind = filter.kind,
            cashFlow = cashFlow.graph,
            cashFlowCategories = cashFlow.categories,
            cashFlowCategoryKeys = cashFlow.selected,
            categoryShares = categoryShares,
            recentIncomes = incomes.take(15),
        )
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

