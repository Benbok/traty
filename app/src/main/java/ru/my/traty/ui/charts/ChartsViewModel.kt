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
import ru.my.traty.data.isUserEditable
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.RU
import ru.my.traty.ui.charts.model.BalancePoint
import ru.my.traty.ui.charts.model.CategoryCreditShare
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.charts.model.ChartSummary
import ru.my.traty.ui.charts.model.DebitCreditBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
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
    val balancePoints: List<BalancePoint> = emptyList(),
    val debitCreditBars: List<DebitCreditBar> = emptyList(),
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

    private val rangeFlow = combine(period, billingPeriod, customRange, startDay) { p, bp, custom, d ->
        val now = LocalDate.now()
        when (p) {
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
    }

    private val expensesFlow = rangeFlow.flatMapLatest { r ->
        c.db.expenses().observeRange(r.startMillis, r.endMillis)
    }

    private val incomesFlow = rangeFlow.flatMapLatest { r ->
        c.db.incomes().observeRange(r.startMillis, r.endMillis)
    }

    private val selection = combine(billingPeriod, startDay) { bp, d -> bp to d }

    val uiState: StateFlow<ChartsUiState> = combine(
        period,
        selection,
        rangeFlow,
        expensesFlow,
        incomesFlow,
    ) { p, sel, range, expenses, incomes ->
        processUiState(p, sel.first, sel.second, range, expenses, incomes)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ChartsUiState(),
    )

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

    fun deleteIncome(income: Income) {
        viewModelScope.launch {
            if (income.isUserEditable) c.db.incomes().delete(income)
        }
    }

    private fun processUiState(
        p: ChartPeriod,
        bp: BillingPeriod,
        startDay: Int,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
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

        val balancePoints = calculateBalancePoints(p, startDay, range, expenses, incomes)
        val debitCreditBars = calculateDebitCreditBars(p, startDay, range, expenses, incomes)
        val categoryShares = calculateCategoryShares(expenses, totalCredit)

        return ChartsUiState(
            period = p,
            billingPeriod = bp,
            periodStartDay = startDay,
            rangeStart = range.startDate,
            rangeEnd = range.endDate,
            summary = summary,
            balancePoints = balancePoints,
            debitCreditBars = debitCreditBars,
            categoryShares = categoryShares,
            recentIncomes = incomes.take(15),
        )
    }

    private fun calculateBalancePoints(
        period: ChartPeriod,
        startDay: Int,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
    ): List<BalancePoint> {
        val daysBetween = ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt()
        if (daysBetween < 0) return emptyList()

        // Сгруппируем операции по дате (LocalDate)
        val debitByDate = incomes.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.amountKopecks } }

        val creditByDate = expenses.groupBy {
            Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.expense.amountKopecks } }

        val points = mutableListOf<BalancePoint>()
        var runningCumulative = 0L

        if (usesDailyPoints(period, range)) {
            // Для месяца и коротких периодов делаем точку на каждый день
            var curDate = range.startDate
            while (!curDate.isAfter(range.endDate)) {
                val debit = debitByDate[curDate] ?: 0L
                val credit = creditByDate[curDate] ?: 0L
                val delta = debit - credit
                runningCumulative += delta
                points.add(
                    BalancePoint(
                        label = "${curDate.dayOfMonth} ${curDate.month.getDisplayName(TextStyle.SHORT, RU)}",
                        date = curDate,
                        deltaKopecks = delta,
                        cumulativeBalanceKopecks = runningCumulative,
                    ),
                )
                curDate = curDate.plusDays(1)
            }
        } else {
            // Для более длинных периодов группируем по расчётным периодам (при дне начала 1 — по месяцам)
            for (bucket in billingBuckets(range.startDate, range.endDate, startDay)) {
                val bucketDebit = incomes.filter {
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() in bucket
                }.sumOf { it.amountKopecks }

                val bucketCredit = expenses.filter {
                    Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() in bucket
                }.sumOf { it.expense.amountKopecks }

                val delta = bucketDebit - bucketCredit
                runningCumulative += delta

                points.add(
                    BalancePoint(
                        label = bucketShortLabel(bucket),
                        date = bucket.endDate,
                        deltaKopecks = delta,
                        cumulativeBalanceKopecks = runningCumulative,
                    ),
                )
            }
        }
        return points
    }

    private fun calculateDebitCreditBars(
        period: ChartPeriod,
        startDay: Int,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
    ): List<DebitCreditBar> {
        val debitByDate = incomes.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.amountKopecks } }

        val creditByDate = expenses.groupBy {
            Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.expense.amountKopecks } }

        val bars = mutableListOf<DebitCreditBar>()

        if (usesDailyPoints(period, range)) {
            var curDate = range.startDate
            while (!curDate.isAfter(range.endDate)) {
                val debit = debitByDate[curDate] ?: 0L
                val credit = creditByDate[curDate] ?: 0L
                bars.add(
                    DebitCreditBar(
                        label = curDate.dayOfMonth.toString(),
                        fullDateLabel = "${curDate.dayOfMonth} ${curDate.month.getDisplayName(TextStyle.FULL, RU)}",
                        date = curDate,
                        debitKopecks = debit,
                        creditKopecks = credit,
                    ),
                )
                curDate = curDate.plusDays(1)
            }
        } else {
            for (bucket in billingBuckets(range.startDate, range.endDate, startDay)) {
                val bucketDebit = incomes.filter {
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() in bucket
                }.sumOf { it.amountKopecks }

                val bucketCredit = expenses.filter {
                    Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() in bucket
                }.sumOf { it.expense.amountKopecks }

                bars.add(
                    DebitCreditBar(
                        label = bucketShortLabel(bucket),
                        fullDateLabel = bucketFullLabel(bucket),
                        date = bucket.startDate,
                        debitKopecks = bucketDebit,
                        creditKopecks = bucketCredit,
                    ),
                )
            }
        }
        return bars
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
