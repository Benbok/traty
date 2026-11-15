package ru.my.traty.ui.debt

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPaymentRow
import ru.my.traty.data.debt.DebtTree
import java.time.LocalDate

/**
 * ViewModel для экрана детального дерева платежей рассрочки / кредита / оплаты частями.
 */
class DebtDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = c.debts
    private val debtIdState = MutableStateFlow<Long?>(null)

    fun load(debtId: Long) {
        debtIdState.value = debtId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val debt: StateFlow<Debt?> = debtIdState
        .flatMapLatest { id -> if (id != null) repo.observeDebt(id) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val payments: StateFlow<List<DebtPaymentRow>> = debtIdState
        .flatMapLatest { id -> if (id != null) repo.observePayments(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<CategoryWithSubs>> = c.db.categories().observeAllWithSubs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tree: StateFlow<DebtTree?> = combine(debt, payments) { d, p ->
        if (d != null) DebtTree.build(d, p) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addPayment(amountKopecks: Long, day: LocalDate = LocalDate.now()) {
        val id = debtIdState.value ?: return
        viewModelScope.launch {
            repo.addPayment(id, amountKopecks, day)
        }
    }

    fun closeDebt() {
        val id = debtIdState.value ?: return
        viewModelScope.launch {
            repo.close(id)
        }
    }

    fun cancelDebt() {
        val id = debtIdState.value ?: return
        viewModelScope.launch {
            repo.cancel(id)
        }
    }

    fun deleteDebt(onDeleted: () -> Unit) {
        val id = debtIdState.value ?: return
        viewModelScope.launch {
            repo.delete(id)
            onDeleted()
        }
    }

    suspend fun getExpense(expenseId: Long): ExpenseWithCategory? {
        return c.db.expenses().getByIds(listOf(expenseId)).firstOrNull()
    }
}
