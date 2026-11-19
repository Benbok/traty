package ru.my.traty.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.Income
import ru.my.traty.data.isUserEditable

/**
 * Строка пополнения со свайпом влево для удаления, как у траты.
 * Запись сверки не свайпается: её правят через «Переделать» на экране сверки.
 */
@Composable
fun SwipeableIncomeRow(
    income: Income,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    internalTransfer: Boolean = false,
) {
    SwipeToDeleteRow(
        key = income.id,
        canDelete = income.isUserEditable,
        onDelete = onDelete,
        modifier = modifier,
    ) {
        IncomeRow(income = income, onClick = onClick, internalTransfer = internalTransfer)
    }
}
