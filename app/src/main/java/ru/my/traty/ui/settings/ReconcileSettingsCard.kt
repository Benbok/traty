package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.reconcile.ReconcilePromptViewModel
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка «Сверка баланса» в настройках: ручной запуск сверки в любой момент. */
@Composable
fun ReconcileSettingsCard(
    onOpenReconcile: () -> Unit,
    onRedoLast: () -> Unit,
    prompt: ReconcilePromptViewModel = viewModel(),
) {
    val hasReconciliation by prompt.hasReconciliation.collectAsStateWithLifecycle()
    TratyCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Сверка баланса", style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                "Сверьте остаток на картах с тем, что насчитало приложение. Расхождение запишется само.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            TratyOutlinedButton(onClick = onOpenReconcile) { Text("Сверить остаток") }
            if (hasReconciliation) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                TratyOutlinedButton(onClick = onRedoLast) { Text("Переделать последнюю сверку") }
            }
        }
    }
}
