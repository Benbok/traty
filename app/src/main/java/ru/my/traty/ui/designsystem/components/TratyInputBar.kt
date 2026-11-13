package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Нижняя панель быстрого ввода трат (текст, голос, ручное добавление).
 */
@Composable
fun TratyInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onManualClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "кофе 250, такси 400…",
    isBusy: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TratyTheme.colors.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Кнопка ручного добавления
            IconButton(
                onClick = onManualClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = TratyTheme.colors.surfaceContainer,
                    contentColor = TratyTheme.colors.onSurface,
                ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить вручную")
            }

            Spacer(Modifier.width(TratyTheme.spacing.sm))

            // Поле ввода с мягким фоном
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(TratyTheme.shapes.medium)
                    .background(TratyTheme.colors.surfaceContainer)
                    .padding(horizontal = TratyTheme.spacing.md, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TratyTheme.typography.bodyMedium.copy(
                        color = TratyTheme.colors.onSurface,
                    ),
                    cursorBrush = SolidColor(TratyTheme.colors.primary),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSendClick() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(TratyTheme.spacing.sm))

            // Действие: Индикатор загрузки, Голос или Отправка
            when {
                isBusy -> {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = TratyTheme.colors.primary,
                        )
                    }
                }
                value.isBlank() -> {
                    IconButton(
                        onClick = onVoiceClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = TratyTheme.colors.primaryContainer,
                            contentColor = TratyTheme.colors.primary,
                        ),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Голосовой ввод")
                    }
                }
                else -> {
                    IconButton(
                        onClick = onSendClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = TratyTheme.colors.primary,
                            contentColor = TratyTheme.colors.onPrimary,
                        ),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Разобрать")
                    }
                }
            }
        }
    }
}
