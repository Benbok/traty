package ru.my.traty.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.PeriodComparison
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.reporting.reportTotals
import ru.my.traty.ui.charts.customChartRange
import java.time.LocalDate
import java.time.ZoneId

/**
 * Произвольный период «с даты по дату» для экрана «Статистика».
 * Пока [range] == null, экран показывает обычный расчётный период (его ведёт MainViewModel);
 * этот класс отвечает только за режим «Даты» и не влияет на ленту трат.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRangeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _range = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val range: StateFlow<Pair<LocalDate, LocalDate>?> = _range

    /** Траты по категориям за выбранные даты (пусто, пока режим «Даты» выключен). */
    val totals: StateFlow<List<CategoryTotal>> = _range
        .flatMapLatest { r -> totalsFor(r) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Сами траты за выбранные даты — для раскрытия категорий (подкатегории, магазины, список). */
    val expenses: StateFlow<List<ExpenseWithCategory>> = _range
        .flatMapLatest { r ->
            if (r == null) flowOf(emptyList())
            else customChartRange(r.first, r.second, zone).let { c.financialReports.expenses(it.startMillis, it.endMillis) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Траты за предыдущий отрезок такой же длины — для сравнения. */
    val prevTotals: StateFlow<List<CategoryTotal>> = _range
        .flatMapLatest { r -> totalsFor(r?.let { previousRange(it.first, it.second) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun totalsFor(r: Pair<LocalDate, LocalDate>?): Flow<List<CategoryTotal>> {
        if (r == null) return flowOf(emptyList())
        val cr = customChartRange(r.first, r.second, zone)
        return c.financialReports.expenses(cr.startMillis, cr.endMillis).map(::reportTotals)
    }

    internal fun observePeriod(period: BillingPeriod, startDay: Int): Flow<StatsPeriodReport> {
        val previous = period.previous(startDay)
        val comparable = PeriodComparison.comparableWindow(period, previous, LocalDate.now(zone))
        val comparableEnd = comparable.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val reports = c.financialReports
        return combine(
            reports.expenses(period.startMillis(zone), period.endMillisExclusive(zone)),
            reports.expenses(previous.startMillis(zone), previous.endMillisExclusive(zone)),
        ) { current, prior ->
            StatsPeriodReport(
                expenses = current,
                previousExpenses = prior,
                totals = reportTotals(current),
                previousTotals = reportTotals(prior),
                comparableTotals = reportTotals(prior.filter { it.expense.timestamp < comparableEnd }),
                comparableWindow = comparable,
            )
        }
    }

    /** Включить режим «Даты»; перепутанные даты меняются местами. */
    fun setRange(a: LocalDate, b: LocalDate) {
        _range.value = if (a.isAfter(b)) b to a else a to b
    }

    /** Вернуться к расчётному периоду. */
    fun clear() {
        _range.value = null
    }

    /** Сдвинуть окно на его длину: [direction] = 1 — вперёд, -1 — назад. */
    fun shift(direction: Int) {
        val r = _range.value ?: return
        _range.value = shiftRange(r.first, r.second, direction)
    }
}

internal data class StatsPeriodReport(
    val expenses: List<ExpenseWithCategory> = emptyList(),
    val previousExpenses: List<ExpenseWithCategory> = emptyList(),
    val totals: List<CategoryTotal> = emptyList(),
    val previousTotals: List<CategoryTotal> = emptyList(),
    val comparableTotals: List<CategoryTotal> = emptyList(),
    val comparableWindow: Pair<LocalDate, LocalDate> = LocalDate.now().let { it to it },
)
