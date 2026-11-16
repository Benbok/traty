package ru.my.traty.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.period.BillingPeriod
import java.time.ZoneId

/**
 * Поиск ленты «Траты» в других периодах. Лента сама ищет в открытом периоде,
 * а здесь — во всех остальных, чтобы показать «за этот период ничего, а в октябре нашлось».
 * Все записи читаются из базы только пока в поиске есть текст.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FeedSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone = ZoneId.systemDefault()

    private val query = MutableStateFlow("")
    private val period = MutableStateFlow<BillingPeriod?>(null)

    fun update(query: String, period: BillingPeriod) {
        this.query.value = query.trim()
        this.period.value = period
    }

    // Очистка поиска убирает найденное сразу, набор текста ждёт паузы
    private val settledQuery = query.debounce { if (it.isEmpty()) 0L else QUERY_DEBOUNCE_MS }.distinctUntilChanged()

    val elsewhere: StateFlow<List<PeriodMatches>> = combine(settledQuery, period) { q, p -> q to p }
        .flatMapLatest { (q, p) ->
            if (q.isEmpty() || p == null) flowOf(emptyList())
            else combine(c.search.observeExpenses(), c.search.observeIncomes(), c.settings.flow, c.search.observeItems()) { e, i, s, items ->
                SearchElsewhere.find(e, i, q, p, s.periodStartDay, zone, items)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Товары покупок по номеру траты, пока в поиске есть текст (без поиска таблица товаров не читается).
     * По ним лента отображает найденные товары и находит покупки в открытом периоде.
     */
    val items: StateFlow<Map<Long, List<ExpenseItem>>> = settledQuery
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(emptyMap()) else c.search.observeItems() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private companion object {
        const val QUERY_DEBOUNCE_MS = 250L
    }
}
