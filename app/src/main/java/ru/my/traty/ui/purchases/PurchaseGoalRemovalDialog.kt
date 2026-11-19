package ru.my.traty.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun PurchaseGoalRemovalDialog(
    title: String,
    message: String,
    action: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TratyTheme.colors.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                Text(message)
                if (error != null) Text(error, color = TratyTheme.colors.expense)
            }
        },
        confirmButton = { TratyTextButton(onClick = onConfirm, enabled = !busy) { Text(action) } },
        dismissButton = { TratyTextButton(onClick = onDismiss, enabled = !busy) { Text("Назад") } },
    )
}
