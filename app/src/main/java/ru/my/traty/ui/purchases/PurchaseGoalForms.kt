package ru.my.traty.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.purchases.PurchaseGoalStatus
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.LocalDate

internal sealed interface PurchaseForm {
    data object Create : PurchaseForm
    data class Contribute(val goalId: Long) : PurchaseForm
    data class Finish(val goalId: Long) : PurchaseForm
    data class ChangeDate(val goalId: Long) : PurchaseForm
    data class Cancel(val goalId: Long) : PurchaseForm
    data class HideCompleted(val goalId: Long) : PurchaseForm
    data class EditContribution(val expenseId: Long) : PurchaseForm
    data class DeleteContribution(val expenseId: Long) : PurchaseForm
}

@Composable
internal fun PurchaseGoalForms(
    form: PurchaseForm,
    goals: List<PurchaseGoalStatus>,
    today: LocalDate,
    vm: PurchaseGoalViewModel,
    onDismiss: () -> Unit,
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val dismiss = { if (!busy) onDismiss() }
    val goal = when (form) {
        is PurchaseForm.Contribute -> goals.firstOrNull { it.goal.id == form.goalId }
        is PurchaseForm.Finish -> goals.firstOrNull { it.goal.id == form.goalId }
        is PurchaseForm.ChangeDate -> goals.firstOrNull { it.goal.id == form.goalId }
        is PurchaseForm.Cancel -> goals.firstOrNull { it.goal.id == form.goalId }
        is PurchaseForm.HideCompleted -> goals.firstOrNull { it.goal.id == form.goalId }
        else -> null
    }

    key(form) {
        when (form) {
            PurchaseForm.Create -> PurchaseFormSheet("Запланировать покупку", error, dismiss) {
                NewPurchaseForm(today, busy) { title, amount, due -> vm.create(title, amount, due, onDismiss) }
            }
            is PurchaseForm.Contribute -> if (goal != null) {
                PurchaseFormSheet("Отложить на покупку", error, dismiss) {
                    ContributePurchaseForm(goal, today, busy) { vm.contribute(form.goalId, it, onDismiss) }
                }
            }
            is PurchaseForm.Finish -> if (goal != null) {
                PurchaseFormSheet("Покупка состоялась", error, dismiss) {
                    FinishPurchaseForm(goal, busy) { vm.finish(form.goalId, it, onDismiss) }
                }
            }
            is PurchaseForm.ChangeDate -> if (goal != null) {
                PurchaseFormSheet("Изменить дату покупки", error, dismiss) {
                    ChangePurchaseDateForm(goal, today, busy) { vm.changeDate(form.goalId, it, onDismiss) }
                }
            }
            is PurchaseForm.Cancel -> if (goal != null) {
                val hasSavings = goal.savedKopecks > 0L
                PurchaseGoalRemovalDialog(
                    title = if (hasSavings) "Отменить накопление?" else "Удалить цель?",
                    message = if (hasSavings) {
                        "Вы отменяете накопление на «${goal.goal.title}». ${formatRub(goal.savedKopecks)} вернутся в доступный остаток, а взносы останутся в истории."
                    } else {
                        "Цель «${goal.goal.title}» исчезнет из списка."
                    },
                    action = if (hasSavings) "Вернуть деньги" else "Удалить",
                    busy = busy,
                    error = error,
                    onDismiss = dismiss,
                ) { vm.cancel(form.goalId, onDismiss) }
            }
            is PurchaseForm.HideCompleted -> if (goal != null) {
                PurchaseGoalRemovalDialog(
                    title = "Скрыть покупку?",
                    message = "Покупка «${goal.goal.title}» исчезнет из списка целей. Все операции останутся в истории.",
                    action = "Скрыть",
                    busy = busy,
                    error = error,
                    onDismiss = dismiss,
                ) { vm.hideCompleted(form.goalId, onDismiss) }
            }
            is PurchaseForm.EditContribution -> {
                val entry = goals.contribution(form.expenseId)
                if (entry != null) {
                    PurchaseFormSheet("Изменить взнос", error, dismiss) {
                        EditPurchaseContributionForm(entry.first, entry.second, busy) {
                            vm.editContribution(form.expenseId, it, onDismiss)
                        }
                    }
                }
            }
            is PurchaseForm.DeleteContribution -> {
                val entry = goals.contribution(form.expenseId)
                if (entry != null) {
                    DeletePurchaseContributionDialog(entry.first, entry.second, busy, error, dismiss) {
                        vm.deleteContribution(form.expenseId, onDismiss)
                    }
                }
            }
        }
    }
}

private fun List<PurchaseGoalStatus>.contribution(id: Long): Pair<PurchaseGoalStatus, Long>? {
    for (status in this) {
        if (!status.isActive) continue
        val expense = status.contributions.firstOrNull { it.id == id } ?: continue
        return status to expense.amountKopecks
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseFormSheet(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text(title, style = TratyTheme.typography.titleMedium)
            content()
            if (error != null) Text(error, color = TratyTheme.colors.expense, style = TratyTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeletePurchaseContributionDialog(
    status: PurchaseGoalStatus,
    amount: Long,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Удалить взнос?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                Text("${formatRub(amount)} вернутся в доступный остаток. Прогресс цели «${status.goal.title}» пересчитается.")
                if (error != null) Text(error, color = TratyTheme.colors.expense)
            }
        },
        confirmButton = { TratyTextButton(onClick = onDelete, enabled = !busy) { Text("Удалить") } },
        dismissButton = { TratyTextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}
