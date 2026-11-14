package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.data.insights.Observation
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка наблюдения над лентой: что заметило приложение. Крестик скрывает её до конца периода. */
@Composable
fun ObservationCard(
    observation: Observation,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Пояснение ИИ, если уже получено. */
    explanation: String? = null,
    /** ИИ сейчас отвечает по этой карточке. */
    explaining: Boolean = false,
    onExplain: (() -> Unit)? = null,
) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Row(
            Modifier.padding(start = TratyTheme.spacing.md, top = TratyTheme.spacing.xs, bottom = TratyTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text(observation.emoji, style = TratyTheme.typography.titleMedium, modifier = Modifier.padding(top = TratyTheme.spacing.sm))
            Column(Modifier.weight(1f).padding(top = TratyTheme.spacing.sm)) {
                Text(observation.title, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
                Text(
                    observation.text,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                if (explanation != null) {
                    Text(
                        explanation,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurface,
                        modifier = Modifier.padding(top = TratyTheme.spacing.sm),
                    )
                } else if (onExplain != null) {
                    TratyTextButton(onClick = onExplain, enabled = !explaining) {
                        Text(if (explaining) "Думаю…" else "✨ Подробнее", style = TratyTheme.typography.labelMedium)
                    }
                }
            }
            TratyIconButton(
                Icons.Filled.Close,
                "Скрыть: ${observation.title}",
                onClick = onDismiss,
                modifier = Modifier.size(40.dp),
                tint = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
