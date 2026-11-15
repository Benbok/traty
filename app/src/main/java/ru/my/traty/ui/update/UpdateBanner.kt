package ru.my.traty.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Плашка на главном экране: вышла новая версия. «Скачать» открывает ссылку в браузере, «Позже» прячет плашку
 * про эту версию. Само приложение ничего не скачивает и не ставит.
 */
@Composable
fun UpdateBanner(
    version: String,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.primaryContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Вышла новая версия $version", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.primary)
            val text = listOfNotNull(
                installedVersion.takeIf { it.isNotBlank() }?.let { "У вас $it." },
                "Скачайте файл и установите его поверх текущей версии.",
            ).joinToString(" ")
            Text(
                text,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = TratyTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                TratyButton(onClick = onDownload) { Text("Скачать") }
                TratyTextButton(onClick = onDismiss) { Text("Позже") }
            }
        }
    }
}
