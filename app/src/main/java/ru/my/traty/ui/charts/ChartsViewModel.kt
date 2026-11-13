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
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.RU
import ru.my.traty.ui.charts.model.BalancePoint
import ru.my.traty.ui.charts.model.CategoryCreditShare
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.charts.model.ChartSummary
import ru.my.traty.ui.charts.model.DebitCreditBar
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class ChartsUiState(
    val period: ChartPeriod = ChartPeriod.MONTH,
    /** Расчётный период вкладки «Месяц» (остальные вкладки — календарные месяцы). */
    val billingPeriod: BillingPeriod = BillingPeriod.containing(LocalDate.now(), 1),
    val periodStartDay: Int = 1,
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

    private val rangeFlow = combine(period, billingPeriod) { p, bp ->
        val now = LocalDate.now()
        when (p) {
            ChartPeriod.MONTH -> {
                ChartRange(bp.startMillis(zone), bp.endMillisExclusive(zone), bp.startDate, bp.endDate)
            }
            ChartPeriod.QUARTER -> {
                val endMonth = YearMonth.from(now)
                val startMonth = endMonth.minusMonths(2)
                ChartRange(startMonth.atDay(1).toStartMillis(), endMonth.plusMonths(1).atDay(1).toStartMillis(), startMonth.atDay(1), endMonth.atEndOfMonth())
            }
            ChartPeriod.HALF_YEAR -> {
                val endMonth = YearMonth.from(now)
                val startMonth = endMonth.minusMonths(5)
                ChartRange(startMonth.atDay(1).toStartMillis(), endMonth.plusMonths(1).atDay(1).toStartMillis(), startMonth.atDay(1), endMonth.atEndOfMonth())
            }
            ChartPeriod.YEAR -> {
                val endMonth = YearMonth.from(now)
                val startMonth = endMonth.minusMonths(11)
                ChartRange(startMonth.atDay(1).toStartMillis(), endMonth.plusMonths(1).atDay(1).toStartMillis(), startMonth.atDay(1), endMonth.atEndOfMonth())
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

    fun prevBillingPeriod() {
        billingPeriod.value = billingPeriod.value.previous(startDay.value)
    }

    fun nextBillingPeriod() {
        billingPeriod.value = billingPeriod.value.next(startDay.value)
    }

    fun addIncome(amountRub: Double, source: String, note: String?, date: LocalDate) {
        val amountKopecks = (amountRub * 100).roundToLong()
        val timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            c.db.incomes().insert(
                Income(
                    amountKopecks = amountKopecks,
                    source = source,
                    note = note,
                    timestamp = timestamp,
                ),
            )
        }
    }

    fun deleteIncome(income: Income) {
        viewModelScope.launch {
            c.db.incomes().delete(income)
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

        val balancePoints = calculateBalancePoints(p, range, expenses, incomes)
        val debitCreditBars = calculateDebitCreditBars(p, range, expenses, incomes)
        val categoryShares = calculateCategoryShares(expenses, totalCredit)

        return ChartsUiState(
            period = p,
            billingPeriod = bp,
            periodStartDay = startDay,
            summary = summary,
            balancePoints = balancePoints,
            debitCreditBars = debitCreditBars,
            categoryShares = categoryShares,
            recentIncomes = incomes.take(15),
        )
    }

    private fun calculateBalancePoints(
        period: ChartPeriod,
        range: ChartRange,
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
    ): List<BalancePoint> {
        val daysBetween = ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt()
        if (daysBetween <= 0) return emptyList()

        // Сгруппируем операции по дате (LocalDate)
        val debitByDate = incomes.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.amountKopecks } }

        val creditByDate = expenses.groupBy {
            Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.expense.amountKopecks } }

        val points = mutableListOf<BalancePoint>()
        var runningCumulative = 0L

        if (period == ChartPeriod.MONTH) {
            // Для месяца делаем точку на каждый день месяца
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
            // Для более длинных периодов группируем по месяцам
            var curMonth = YearMonth.from(range.startDate)
            val endMonth = YearMonth.from(range.endDate)

            while (!curMonth.isAfter(endMonth)) {
                val monthStart = curMonth.atDay(1)
                val monthEnd = curMonth.atEndOfMonth()

                val monthDebit = incomes.filter {
                    val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && !d.isAfter(monthEnd)
                }.sumOf { it.amountKopecks }

                val monthCredit = expenses.filter {
                    val d = Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && !d.isAfter(monthEnd)
                }.sumOf { it.expense.amountKopecks }

                val delta = monthDebit - monthCredit
                runningCumulative += delta

                points.add(
                    BalancePoint(
                        label = curMonth.month.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() },
                        date = monthEnd,
                        deltaKopecks = delta,
                        cumulativeBalanceKopecks = runningCumulative,
                    ),
                )
                curMonth = curMonth.plusMonths(1)
            }
        }
        return points
    }

    private fun calculateDebitCreditBars(
        period: ChartPeriod,
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

        if (period == ChartPeriod.MONTH) {
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
            var curMonth = YearMonth.from(range.startDate)
            val endMonth = YearMonth.from(range.endDate)

            while (!curMonth.isAfter(endMonth)) {
                val monthStart = curMonth.atDay(1)
                val monthEnd = curMonth.atEndOfMonth()

                val monthDebit = incomes.filter {
                    val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && !d.isAfter(monthEnd)
                }.sumOf { it.amountKopecks }

                val monthCredit = expenses.filter {
                    val d = Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()
                    !d.isBefore(monthStart) && !d.isAfter(monthEnd)
                }.sumOf { it.expense.amountKopecks }

                bars.add(
                    DebitCreditBar(
                        label = curMonth.month.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() },
                        fullDateLabel = "${curMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, RU)} ${curMonth.year}",
                        date = monthStart,
                        debitKopecks = monthDebit,
                        creditKopecks = monthCredit,
                    ),
                )
                curMonth = curMonth.plusMonths(1)
            }
        }
        return bars
    }

    private fun calculateCategoryShares(
        expenses: List<ExpenseWithCategory>,
        totalCredit: Long,
    ): List<CategoryCreditShare> {
        if (totalCredit <= 0) return emptyList()
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
                )
            }
            .sortedByDescending { it.totalKopecks }
    }

    private fun LocalDate.toStartMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
}
