package ru.my.traty.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.data.items.ItemGroup
import ru.my.traty.data.items.ItemGroups

internal data class ItemGroupsSheetState(
    val categoryId: Long,
    val categoryName: String,
    val categoryEmoji: String,
    val periodLabel: String,
    val groups: List<ItemGroup> = emptyList(),
    val query: String = "",
    val selectedGroup: ItemGroup? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Состояние шторки «Товары» для выбранной категории и расчётного периода на экране «Статистика».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemGroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    private val window = MutableStateFlow<Pair<Long, Long>?>(null)

    /** Категории, у которых в текущем периоде есть хотя бы одна позиция чека. */
    val categoriesWithItems: StateFlow<Set<Long>> = window
        .flatMapLatest { w ->
            if (w == null) flowOf(emptySet()) else c.expenseItems.observeCategoryIdsWithItems(w.first, w.second)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _sheet = MutableStateFlow<ItemGroupsSheetState?>(null)
    internal val sheet: StateFlow<ItemGroupsSheetState?> = _sheet

    private var loadJob: Job? = null

    fun setWindow(fromMillis: Long, toMillis: Long) {
        val next = fromMillis to toMillis
        if (window.value != next) window.value = next
    }

    fun open(
        categoryId: Long,
        categoryName: String,
        categoryEmoji: String,
        periodLabel: String,
        fromMillis: Long,
        toMillis: Long,
    ) {
        loadJob?.cancel()
        _sheet.value = ItemGroupsSheetState(
            categoryId = categoryId,
            categoryName = categoryName,
            categoryEmoji = categoryEmoji,
            periodLabel = periodLabel,
            loading = true,
        )
        loadJob = viewModelScope.launch {
            try {
                val sales = withContext(Dispatchers.IO) {
                    c.expenseItems.getSalesForCategory(categoryId, fromMillis, toMillis)
                }
                val groups = withContext(Dispatchers.Default) {
                    ItemGroups.build(sales)
                }
                val current = _sheet.value ?: return@launch
                if (current.categoryId == categoryId) {
                    _sheet.value = current.copy(groups = groups, loading = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val current = _sheet.value ?: return@launch
                if (current.categoryId == categoryId) {
                    _sheet.value = current.copy(loading = false, error = "Не удалось загрузить товары")
                }
            }
        }
    }

    fun updateQuery(query: String) {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(query = query)
    }

    fun selectGroup(group: ItemGroup) {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(selectedGroup = group)
    }

    fun clearSelection() {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(selectedGroup = null)
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        _sheet.value = null
    }
}
