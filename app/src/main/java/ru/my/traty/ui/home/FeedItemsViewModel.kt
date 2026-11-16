package ru.my.traty.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp

/**
 * Сколько товаров в каждой покупке: по этому числу лента пишет «5 товаров» вместо длинного списка в заметке.
 * Читаются только числа, сами товары лента не грузит (они нужны поиску, см. [FeedSearchViewModel]).
 */
class FeedItemsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    /** Номер траты → число товаров в ней. Нет в карте — товаров нет. */
    val counts: StateFlow<Map<Long, Int>> = c.expenseItems.observeCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
