package ru.my.traty.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.ExpenseWithCategory
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
            else customChartRange(r.first, r.second, zone).let { c.db.expenses().observeRange(it.startMillis, it.endMillis) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Траты за предыдущий отрезок такой же длины — для сравнения. */
    val prevTotals: StateFlow<List<CategoryTotal>> = _range
        .flatMapLatest { r -> totalsFor(r?.let { previousRange(it.first, it.second) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun totalsFor(r: Pair<LocalDate, LocalDate>?): Flow<List<CategoryTotal>> {
        if (r == null) return flowOf(emptyList())
        val cr = customChartRange(r.first, r.second, zone)
        return c.db.expenses().observeTotals(cr.startMillis, cr.endMillis)
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
