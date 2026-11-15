package ru.my.traty.ui.debt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import ru.my.traty.data.debt.DeletionImpact
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

    /** Сообщение об ошибке для экрана: показывается один раз и сбрасывается через [errorShown]. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun errorShown() {
        _error.value = null
    }

    /** Что исчезнет при удалении: заполняется перед диалогом «Удалить покупку?». */
    private val _deletion = MutableStateFlow<DeletionImpact?>(null)
    val deletion: StateFlow<DeletionImpact?> = _deletion

    fun addPayment(amountKopecks: Long, day: LocalDate = LocalDate.now()) {
        val id = debtIdState.value ?: return
        run("Не удалось внести платёж. Попробуйте ещё раз.") {
            if (!repo.addPayment(id, amountKopecks, day)) _error.value = "Покупка уже закрыта или удалена, платёж не внесён."
        }
    }

    fun closeDebt() {
        val id = debtIdState.value ?: return
        run("Не удалось закрыть покупку. Попробуйте ещё раз.") { repo.close(id) }
    }

    fun cancelDebt() {
        val id = debtIdState.value ?: return
        run("Не удалось отменить покупку. Попробуйте ещё раз.") { repo.cancel(id) }
    }

    /** Открывает диалог удаления: сначала считаем, что именно удалится. */
    fun askDelete() {
        val id = debtIdState.value ?: return
        run("Не удалось подготовить удаление. Попробуйте ещё раз.") { _deletion.value = repo.deletionImpact(id) }
    }

    fun cancelDelete() {
        _deletion.value = null
    }

    fun deleteDebt(onDeleted: () -> Unit) {
        val id = debtIdState.value ?: return
        _deletion.value = null
        run("Не удалось удалить покупку. Попробуйте ещё раз.") {
            repo.delete(id)
            onDeleted()
        }
    }

    private fun run(errorText: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = errorText
            }
        }
    }

    suspend fun getExpense(expenseId: Long): ExpenseWithCategory? {
        return c.db.expenses().getByIds(listOf(expenseId)).firstOrNull()
    }
}
