package ru.my.traty.ui.debt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyDropdownMenuItem
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratySnackbar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.pluralRu

/**
 * Экран дерева и графика платежей по покупке частями (рассрочка, кредит, аванс).
 * Карточка сводки — [DebtSummaryCard], строки дерева — [DebtTreeNodeRow], список — [DebtTreeList].
 */
@Composable
fun DebtDetailScreen(
    debtId: Long,
    onBack: () -> Unit,
    onOpenExpense: (ExpenseWithCategory) -> Unit,
    vm: DebtDetailViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(debtId) {
        vm.load(debtId)
    }

    val debt by vm.debt.collectAsStateWithLifecycle()
    val tree by vm.tree.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(TreeFilter.ALL) }
    var payAmountText by rememberSaveable { mutableStateOf<String?>(null) }
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.errorShown()
        }
    }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) { TratySnackbar(it) } },
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = debt?.title ?: "Покупка частями",
                subtitle = debt?.let { kindSubtitle(it) } ?: "Дерево платежей",
                onBack = onBack,
                actions = {
                    DebtDetailMenu(
                        active = debt?.state == DebtState.ACTIVE,
                        onClose = vm::closeDebt,
                        onCancel = vm::cancelDebt,
                        onDelete = vm::askDelete,
                    )
                },
            )
        },
    ) { pad ->
        val currentDebt = debt
        val currentTree = tree
        if (currentDebt == null || currentTree == null) {
            DebtDetailLoading(pad)
        } else {
            DebtTreeList(
                debt = currentDebt,
                tree = currentTree,
                category = categories.firstOrNull { it.category.id == currentDebt.categoryId },
                filter = filter,
                onFilter = { filter = it },
                onPay = { kopecks -> payAmountText = AmountInput.fromKopecks(kopecks.coerceAtLeast(100L)) },
                onOpenExpense = { expenseId ->
                    scope.launch {
                        val exp = vm.getExpense(expenseId)
                        if (exp != null) onOpenExpense(exp)
                    }
                },
                pad = pad,
            )
        }
    }

    payAmountText?.let { rawAmount ->
        AddPaymentDialog(
            initialAmount = rawAmount,
            onDismiss = { payAmountText = null },
            onConfirm = { kopecks ->
                vm.addPayment(kopecks)
                payAmountText = null
            },
        )
    }
    DeleteDebtHost(vm, onDeleted = onBack)
}

/** Диалог удаления: показывается, когда ViewModel посчитал, что именно исчезнет. */
@Composable
private fun DeleteDebtHost(vm: DebtDetailViewModel, onDeleted: () -> Unit) {
    val deletion by vm.deletion.collectAsStateWithLifecycle()
    deletion?.let { impact ->
        DeleteDebtDialog(
            text = DebtDeleteText.of(impact),
            onDismiss = vm::cancelDelete,
            onConfirm = { vm.deleteDebt(onDeleted = onDeleted) },
        )
    }
}

/** Меню «⋮» в шапке: закрыть, отменить, удалить. */
@Composable
private fun DebtDetailMenu(active: Boolean, onClose: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        TratyIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "Действия",
            onClick = { showMenu = true },
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (active) {
                TratyDropdownMenuItem(
                    text = { Text("Отметить как закрытую") },
                    onClick = {
                        showMenu = false
                        onClose()
                    },
                )
                TratyDropdownMenuItem(
                    text = { Text("Отменить покупку (возврат)") },
                    onClick = {
                        showMenu = false
                        onCancel()
                    },
                )
            }
            TratyDropdownMenuItem(
                text = { Text("Удалить запись", color = TratyTheme.colors.expense) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DebtDetailLoading(pad: PaddingValues) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(pad),
        contentAlignment = Alignment.Center,
    ) {
        TratyEmptyState(
            title = "Загрузка...",
            description = "Получаем график и историю платежей",
            icon = Icons.Filled.Schedule,
        )
    }
}

@Composable
private fun DeleteDebtDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить покупку?", style = TratyTheme.typography.titleMedium) },
        text = {
            Text(text, style = TratyTheme.typography.bodyMedium)
        },
        confirmButton = {
            TratyButton(
                onClick = onConfirm,
                containerColor = TratyTheme.colors.expense,
                contentColor = Color.White,
            ) { Text("Удалить") }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun kindSubtitle(debt: Debt): String = when (debt.debtKind) {
    DebtKind.INSTALLMENT -> {
        val count = debt.paymentsCount?.let { " на $it ${pluralRu(it.toLong(), "платёж", "платежа", "платежей")}" } ?: ""
        "Рассрочка$count"
    }
    DebtKind.LOAN -> {
        val rate = if (debt.rateBasisPoints > 0) " под ${debt.rateBasisPoints / 100}%" else ""
        "Кредит$rate"
    }
    DebtKind.PARTIAL -> "Оплата частями (аванс и доплаты)"
}
