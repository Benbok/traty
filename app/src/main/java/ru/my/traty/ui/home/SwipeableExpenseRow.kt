package ru.my.traty.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.isUserEditable
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.labels.ExpenseLabelHit

/** Строка траты со свайпом влево для удаления (см. [SwipeToDeleteRow]). */
@Composable
fun SwipeableExpenseRow(
    item: ExpenseWithCategory,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    labels: List<ExpenseLabelHit> = emptyList(),
    /** Товары, из-за которых трата нашлась поиском; без поиска пусто. */
    matchedItems: List<ExpenseItem> = emptyList(),
    /** Сколько товаров в покупке (пишется вместо длинной заметки). */
    itemCount: Int = 0,
) {
    SwipeToDeleteRow(
        key = item.expense.id,
        canDelete = item.expense.isUserEditable,
        onDelete = onDelete,
        modifier = modifier,
    ) {
        ExpenseRowWithMatches(
            row = item,
            onClick = onClick,
            matched = matchedItems,
            labels = labels,
            itemCount = itemCount,
        )
    }
}
