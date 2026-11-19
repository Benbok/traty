package ru.my.traty.ui.home

import androidx.compose.runtime.Composable
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.ui.income.DeleteIncomeConfirmDialog
import ru.my.traty.ui.income.IncomeDetailsDialog
import java.time.LocalDate

@Composable
internal fun HomeFeedDialogs(
    pickerDate: LocalDate?,
    onPickDate: (LocalDate) -> Unit,
    onDismissPicker: () -> Unit,
    openedIncome: Income?,
    onDismissIncome: () -> Unit,
    incomeToDelete: Income?,
    onDismissIncomeDelete: () -> Unit,
    onDeleteIncome: (Income) -> Unit,
    expenseToDelete: ExpenseWithCategory?,
    onDismissExpenseDelete: () -> Unit,
    onDeleteExpense: (Expense) -> Unit,
) {
    if (pickerDate != null) {
        DatePickerModal(pickerDate, onPickDate, onDismissPicker)
    }
    openedIncome?.let { income ->
        IncomeDetailsDialog(
            income = income,
            onDelete = {
                onDeleteIncome(income)
                onDismissIncome()
            },
            onDismiss = onDismissIncome,
        )
    }
    incomeToDelete?.let { income ->
        DeleteIncomeConfirmDialog(
            income = income,
            onConfirm = {
                onDeleteIncome(income)
                onDismissIncomeDelete()
            },
            onDismiss = onDismissIncomeDelete,
        )
    }
    expenseToDelete?.let { item ->
        DeleteExpenseConfirmDialog(
            item = item,
            onConfirm = {
                onDeleteExpense(item.expense)
                onDismissExpenseDelete()
            },
            onDismiss = onDismissExpenseDelete,
        )
    }
}
