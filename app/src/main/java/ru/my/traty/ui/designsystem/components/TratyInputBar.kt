package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    isIncomeMode: Boolean = false,
    onToggleIncomeMode: (() -> Unit)? = null,
) {
    val accent = if (isIncomeMode) TratyTheme.colors.income else TratyTheme.colors.primary
    val fieldColor = if (isIncomeMode) TratyTheme.colors.incomeContainer else TratyTheme.colors.surfaceContainer
    val hint = if (isIncomeMode) "зарплата 80000, аванс 30 тысяч…" else placeholder
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

            // Переключатель режима: «−» трата (по умолчанию), «+» пополнение
            if (onToggleIncomeMode != null) {
                val modeLabel = if (isIncomeMode) "Режим: пополнение" else "Режим: трата"
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 32.dp)
                        .clip(TratyTheme.shapes.small)
                        .background(if (isIncomeMode) TratyTheme.colors.income else TratyTheme.colors.expenseContainer)
                        .clickable(onClick = onToggleIncomeMode)
                        .semantics {
                            contentDescription = modeLabel
                            role = Role.Switch
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isIncomeMode) "+" else "−",
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncomeMode) TratyTheme.colors.onPrimary else TratyTheme.colors.expense,
                    )
                }
                Spacer(Modifier.width(TratyTheme.spacing.sm))
            }

            // Поле ввода с мягким фоном
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(TratyTheme.shapes.medium)
                    .background(fieldColor)
                    .padding(horizontal = TratyTheme.spacing.md, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
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
                    cursorBrush = SolidColor(accent),
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
                            color = accent,
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
                            containerColor = accent,
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
