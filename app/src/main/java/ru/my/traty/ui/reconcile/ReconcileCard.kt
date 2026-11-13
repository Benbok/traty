package ru.my.traty.ui.reconcile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка на Главной в конце периода; не блокирует работу с приложением. */
@Composable
fun ReconcileCard(onReconcile: () -> Unit, onSnooze: () -> Unit, modifier: Modifier = Modifier) {
    TratyTonalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        backgroundColor = TratyTheme.colors.primaryContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text(
                "Период закончился — сколько у вас сейчас денег на всех картах вместе?",
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.onPrimaryContainer,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TratyButton(onClick = onReconcile) { Text("Сверить остаток") }
                TratyTextButton(onClick = onSnooze) { Text("Напомнить завтра") }
            }
        }
    }
}
