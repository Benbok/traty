package ru.my.traty.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.data.Income
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime

/** Подтверждение удаления пополнения после свайпа влево в ленте «Трат». */
@Composable
fun DeleteIncomeConfirmDialog(income: Income, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                text = "Удалить пополнение?",
                style = TratyTheme.typography.headlineMedium,
                color = TratyTheme.colors.onSurface,
            )
        },
        text = { DeleteIncomeBody(income) },
        confirmButton = {
            TratyButton(
                onClick = onConfirm,
                containerColor = TratyTheme.colors.expense,
                contentColor = Color.White,
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun DeleteIncomeBody(income: Income) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        TratyTonalCard(modifier = Modifier.fillMaxWidth(), shape = TratyTheme.shapes.medium) {
            Column(
                modifier = Modifier.padding(TratyTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                Text(income.source, style = TratyTheme.typography.titleMedium, color = TratyTheme.colors.onSurface)
                Text(
                    "+${formatRub(income.amountKopecks)}",
                    style = TratyTheme.typography.headlineMedium,
                    color = TratyTheme.colors.income,
                )
                val sub = listOfNotNull(formatTime(income.timestamp), income.note?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                Text(sub, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
        Text(
            text = "Пополнение пропадёт из ленты и баланса.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}
