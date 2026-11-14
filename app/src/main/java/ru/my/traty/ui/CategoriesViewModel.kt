package ru.my.traty.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryException
import ru.my.traty.data.CategoryWithSubs

/** Редактор категорий: все категории, включая архивные. */
class CategoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = c.categories

    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeAllWithSubs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var message by mutableStateOf<String?>(null)

    /** Выполняет операцию; ошибку показывает в снекбаре. [onDone] — только при успехе. */
    private fun run(onDone: () -> Unit = {}, block: suspend () -> String?) = viewModelScope.launch {
        try {
            block()?.let { message = it }
            onDone()
        } catch (e: CategoryException) {
            message = e.message
        }
    }

    fun add(name: String, emoji: String, onDone: () -> Unit) = run(onDone) { repo.addCategory(name, emoji); null }

    fun update(id: Long, name: String, emoji: String, color: Int?, isImpulsive: Boolean, onDone: () -> Unit) =
        run(onDone) { repo.updateCategory(id, name, emoji, color, isImpulsive); "Сохранено" }

    fun setArchived(id: Long, archived: Boolean, onDone: () -> Unit) =
        run(onDone) { repo.setArchived(id, archived); if (archived) "Категория в архиве" else "Категория возвращена" }

    fun move(id: Long, up: Boolean) = run { repo.move(id, up); null }

    fun merge(fromId: Long, intoId: Long, onDone: () -> Unit) =
        run(onDone) { "Перенесено трат: ${repo.merge(fromId, intoId)}" }

    fun addSub(categoryId: Long, name: String) = run { repo.addSubcategory(categoryId, name); null }
    fun renameSub(id: Long, name: String) = run { repo.renameSubcategory(id, name); null }
    fun deleteSub(id: Long) = run { repo.deleteSubcategory(id); null }

    suspend fun expenseCount(categoryId: Long): Int = c.db.expenses().countByCategory(categoryId)
}
