package ru.my.traty.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Выход из правки с несохранёнными изменениями: сохранить, выйти без сохранения или остаться. */
@Composable
fun DiscardChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
    message: String = "Вы поменяли трату, но ещё не сохранили её.",
) {
    AlertDialog(
        onDismissRequest = onStay,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Сохранить изменения?", style = TratyTheme.typography.titleMedium) },
        text = { Text(message, style = TratyTheme.typography.bodyMedium) },
        confirmButton = { TratyButton(onClick = onSave) { Text("Сохранить") } },
        dismissButton = {
            TratyTextButton(onClick = onDiscard, contentColor = TratyTheme.colors.expense) { Text("Не сохранять") }
        },
    )
}
