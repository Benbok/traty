package ru.my.traty.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.capture.DraftItem

/** Товары траты на экране правки: чтение и сохранение позиций. Сама трата сохраняется в [ru.my.traty.ui.MainViewModel]. */
class ExpenseItemsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TratyApp).container.expenseItems

    /** Ошибка сохранения товаров, понятным текстом; null — ошибки нет. */
    var error by mutableStateOf<String?>(null)
        private set

    fun observe(expenseId: Long): Flow<List<DraftItem>> = repo.observeFor(expenseId).map { list -> list.map(DraftItem::fromSaved) }

    fun save(expenseId: Long, items: List<DraftItem>) = viewModelScope.launch {
        try {
            repo.replace(expenseId, items.map(DraftItem::toExpenseItem))
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось сохранить товары: ${e.message ?: "ошибка базы"}"
        }
    }

    fun clearError() {
        error = null
    }
}
