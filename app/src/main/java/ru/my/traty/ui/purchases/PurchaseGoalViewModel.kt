package ru.my.traty.ui.purchases

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.purchases.PurchaseGoalStatus
import java.time.LocalDate

class PurchaseGoalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TratyApp).container.purchaseGoals
    val goals: StateFlow<List<PurchaseGoalStatus>?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun create(title: String, amount: Long, due: LocalDate, onSaved: () -> Unit) =
        save("Не удалось создать цель", onSaved) { repository.create(title, amount, due) }

    fun changeDate(goalId: Long, due: LocalDate, onSaved: () -> Unit) =
        save("Не удалось изменить дату", onSaved) { repository.reschedule(goalId, due) }

    fun contribute(goalId: Long, amount: Long, onSaved: () -> Unit) =
        save("Не удалось записать взнос", onSaved) { repository.contribute(goalId, amount) }

    fun finish(goalId: Long, amount: Long, onSaved: () -> Unit) =
        save("Не удалось завершить цель", onSaved) { repository.finish(goalId, amount) }

    fun cancel(goalId: Long, onSaved: () -> Unit) =
        save("Не удалось отменить накопление", onSaved) { repository.cancel(goalId) }

    fun hideCompleted(goalId: Long, onSaved: () -> Unit) =
        save("Не удалось скрыть покупку", onSaved) { repository.hideCompleted(goalId) }

    fun editContribution(expenseId: Long, amount: Long, onSaved: () -> Unit) =
        save("Не удалось изменить взнос", onSaved) { repository.editContribution(expenseId, amount) }

    fun deleteContribution(expenseId: Long, onSaved: () -> Unit) =
        save("Не удалось удалить взнос", onSaved) { repository.deleteContribution(expenseId) }

    private fun save(failure: String, onSaved: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        clearError()
        viewModelScope.launch {
            try {
                action()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = failure
            } finally {
                _busy.value = false
            }
        }
    }
}
