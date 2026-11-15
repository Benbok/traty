package ru.my.traty.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.my.traty.data.Income
import ru.my.traty.data.isUserEditable
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime

/**
 * Карточка дохода: откуда, сколько, когда и заметка. Удалить можно обычный доход;
 * запись сверки правится только через «Переделать» на экране сверки.
 */
@Composable
fun IncomeDetailsDialog(income: Income, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text(income.source, style = TratyTheme.typography.titleMedium) },
        text = { IncomeDetailsBody(income) },
        confirmButton = {
            if (income.isUserEditable) {
                TratyTextButton(onClick = onDelete, contentColor = TratyTheme.colors.expense) {
                    Text("Удалить")
                }
            } else {
                Text(
                    "Запись сверки",
                    style = TratyTheme.typography.labelLarge,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun IncomeDetailsBody(income: Income) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Text(
            "+${formatRub(income.amountKopecks)}",
            style = TratyTheme.typography.headlineMedium,
            color = TratyTheme.colors.income,
        )
        Text(
            formatTime(income.timestamp),
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        income.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = TratyTheme.typography.bodyMedium)
        }
    }
}
