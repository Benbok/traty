package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.AppSettings
import ru.my.traty.data.DEFAULT_RECEIPT_MODEL
import ru.my.traty.data.canScanReceipts
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Карточка «Чеки по фото»: работают только через Яндекс, с тем же API-ключом и ID каталога, что и YandexGPT,
 * даже если для голоса выбран DeepSeek. Пока ключ и каталог не заполнены, кнопки камеры на «Тратах» нет.
 */
@Composable
internal fun ReceiptSettingsCard(s: AppSettings, onChange: (AppSettings) -> Unit) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text("Чеки по фото", style = TratyTheme.typography.titleMedium)
            Text(
                "Фото чека читает Yandex Vision OCR, товары раскладывает YandexGPT. Нужны API-ключ и ID каталога " +
                    "Yandex Cloud из карточки выше, у сервисного аккаунта — роли ai.vision.user и ai.languageModels.user.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                if (s.canScanReceipts) "Включено: на «Тратах» рядом с микрофоном есть кнопка камеры"
                else "Выключено: заполните API-ключ и ID каталога Yandex Cloud и сохраните",
                style = TratyTheme.typography.bodySmall,
                color = if (s.canScanReceipts) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
            )
            OutlinedTextField(
                value = s.yandexReceiptModel,
                onValueChange = { onChange(s.copy(yandexReceiptModel = it)) },
                label = { Text("Модель для чеков (напр. $DEFAULT_RECEIPT_MODEL)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            )
        }
    }
}
