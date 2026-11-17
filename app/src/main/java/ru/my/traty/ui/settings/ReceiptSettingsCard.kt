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

/**
 * Карточка «Чеки по фото»: текст с фото читает Yandex Vision OCR (ключ и ID каталога из карточки YandexGPT),
 * товары раскладывает DeepSeek (его ключ), какой бы ИИ ни был выбран для голоса.
 * Пока что-то из этого не заполнено, кнопки камеры на «Тратах» нет.
 */
@Composable
internal fun ReceiptSettingsCard(s: AppSettings) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text("Чеки по фото", style = TratyTheme.typography.titleMedium)
            Text(
                "Текст с фото читает Yandex Vision OCR, товары из текста раскладывает DeepSeek. Нужны ключ DeepSeek " +
                    "и API-ключ с ID каталога Yandex Cloud из карточек выше, у сервисного аккаунта — роль ai.vision.user.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                if (s.canScanReceipts) "Включено: на «Тратах» рядом с микрофоном есть кнопка камеры"
                else "Выключено: заполните ключ DeepSeek, API-ключ и ID каталога Yandex Cloud и сохраните",
                style = TratyTheme.typography.bodySmall,
                color = if (s.canScanReceipts) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
