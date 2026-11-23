package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.AppSettings
import ru.my.traty.data.canScanReceipts
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Настройка распознавания фото чека независимо от ИИ для голосового ввода. */
@Composable
internal fun ReceiptSettingsCard(s: AppSettings) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text("Чеки по фото", style = TratyTheme.typography.titleMedium)
            Text(
                "Для распознавания фото используется DeepSeek Flash и сохранённый ключ DeepSeek.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                if (s.canScanReceipts) "Включено: на «Тратах» рядом с микрофоном есть кнопка «Чек»"
                else "Выключено: укажите ключ DeepSeek и сохраните настройки",
                style = TratyTheme.typography.bodySmall,
                color = if (s.canScanReceipts) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
