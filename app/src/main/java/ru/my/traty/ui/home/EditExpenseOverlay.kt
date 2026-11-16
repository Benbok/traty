package ru.my.traty.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.capture.DraftItem
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Экран траты поверх вкладок вместе с данными: покупка частями и товары.
 * Экран показывается, когда товары прочитаны, чтобы правка не начиналась с пустого списка.
 */
@Composable
fun EditExpenseOverlay(
    item: ExpenseWithCategory,
    vm: MainViewModel,
    itemsVm: ExpenseItemsViewModel,
    categories: List<CategoryWithSubs>,
    labels: List<ExpenseLabelHit>,
    onOpenDebt: (Long) -> Unit,
) {
    val linkedDebt by vm.observeDebtForExpense(item.expense.id).collectAsStateWithLifecycle(initialValue = null)
    val itemsFlow = remember(item.expense.id) { itemsVm.observe(item.expense.id) }
    val savedItems: List<DraftItem>? by itemsFlow.collectAsStateWithLifecycle(initialValue = null)
    // Ошибку сохранения товаров показываем тем же сообщением, что и остальные
    LaunchedEffect(itemsVm.error) {
        itemsVm.error?.let { vm.message = it; itemsVm.clearError() }
    }
    Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
        val loaded = savedItems ?: return@Surface
        EditExpenseScreen(
            item = item,
            categories = categories,
            labels = labels,
            linkedDebt = linkedDebt,
            onOpenDebt = onOpenDebt,
            items = loaded,
            onDismiss = { vm.editing = null },
            onSave = { fields ->
                if (fields.items != loaded) itemsVm.save(item.expense.id, fields.items)
                vm.updateExpense(item, fields)
            },
            onDelete = {
                vm.delete(item.expense)
                vm.editing = null
            },
            onSetCategoryImpulsive = vm::setCategoryImpulsive,
        )
    }
}
