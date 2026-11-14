package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Нижний лист панели быстрой записи: кнопка микрофона, потом «Сохранено · Изменить» или «Нет сети». */
@Composable
fun RecordPanel(
    ui: CaptureUi,
    recordState: RecordState,
    busy: Boolean,
    error: String?,
    onMicClick: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = TratyTheme.colors.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            when (ui) {
                is CaptureUi.Recording -> {
                    MicButton(state = recordState, busy = busy, onClick = onMicClick)
                    if (error != null) {
                        Text(error, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.expense)
                    }
                    TratyTextButton(onClick = onCancel) { Text("Отмена") }
                }
                is CaptureUi.Saved -> {
                    Text(
                        "Сохранено ${ui.result.saved.count}",
                        style = TratyTheme.typography.titleMedium,
                        color = TratyTheme.colors.onSurface,
                    )
                    if (ui.result.recurringAdded.isNotEmpty()) {
                        Text(
                            "В подписки: ${ui.result.recurringAdded.joinToString()}",
                            style = TratyTheme.typography.bodyMedium,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                    TratyButton(onClick = onEdit) { Text("Изменить") }
                }
                is CaptureUi.Queued -> Text(
                    "Нет сети, разберу позже",
                    style = TratyTheme.typography.titleMedium,
                    color = TratyTheme.colors.onSurface,
                )
                // Проверка идёт в диалоге поверх панели
                is CaptureUi.Confirm -> Unit
            }
        }
    }
}
