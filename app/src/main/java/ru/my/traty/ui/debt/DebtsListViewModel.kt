package ru.my.traty.ui.debt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPaymentRow
import ru.my.traty.data.debt.DebtTree
import ru.my.traty.data.debt.DebtTreeSummary

data class DebtListItem(
    val debt: Debt,
    val summary: DebtTreeSummary,
    val category: CategoryWithSubs?,
)

/**
 * ViewModel для списка покупок частями, рассрочек и кредитов.
 */
class DebtsListViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = c.debts

    val debts: StateFlow<List<Debt>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments: StateFlow<List<DebtPaymentRow>> = repo.observePayments()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<CategoryWithSubs>> = c.db.categories().observeAllWithSubs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<DebtListItem>> = combine(debts, payments, categories) { debtList, paymentList, catList ->
        debtList.map { debt ->
            val debtPayments = paymentList.filter { it.debtId == debt.id }
            val tree = DebtTree.build(debt, debtPayments)
            val cat = catList.firstOrNull { it.category.id == debt.categoryId }
            DebtListItem(debt, tree.summary, cat)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
