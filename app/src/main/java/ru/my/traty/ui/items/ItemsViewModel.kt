package ru.my.traty.ui.items

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import ru.my.traty.TratyApp
import ru.my.traty.data.items.ItemSale
import ru.my.traty.data.items.ItemTotals
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.data.items.StoreItems
import java.time.LocalDate
import java.time.ZoneId

/** Что показать на экране «Товары»: товары за даты, магазины и выбранный магазин. */
data class ItemsState(
    val products: List<ProductTotal> = emptyList(),
    val stores: List<StoreItems> = emptyList(),
    val store: String? = null,
    val sales: List<ItemSale> = emptyList(),
)

/**
 * Траты по товарам за отрезок дат: суммы по каждому товару, по всем магазинам или внутри одного.
 * Считает [ItemTotals] (чистый Kotlin), ViewModel только подставляет даты и магазин.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TratyApp).container.expenseItems
    private val zone: ZoneId = ZoneId.systemDefault()
    private val window = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val store = MutableStateFlow<String?>(null)

    private val sales = window.flatMapLatest { w ->
        if (w == null) flowOf(emptyList())
        else repo.observeSales(w.first.atStartOfDay(zone).toInstant().toEpochMilli(), w.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    val state: StateFlow<ItemsState> = combine(sales, store) { list, s ->
        ItemsState(products = ItemTotals.byProduct(list, s), stores = ItemTotals.stores(list), store = s, sales = list)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ItemsState())

    /** Даты включительно: с [from] по [to]. */
    fun setWindow(from: LocalDate, to: LocalDate) {
        window.value = if (from.isAfter(to)) to to from else from to to
    }

    /** null — все магазины. */
    fun selectStore(name: String?) {
        store.value = name
    }
}
