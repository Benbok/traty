package ru.my.traty.ui.capture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Кнопка микрофона — единственный способ ввода на главной и в панели быстрой записи.
 * Кольцо вокруг кнопки пульсирует по громкости.
 * Обычный вид (панель быстрой записи) — большая кнопка с подсказкой или промежуточным текстом под ней.
 * [compact] (экран «Траты») — кнопка поменьше и без текста; ошибки показывает сам экран.
 */
@Composable
fun MicButton(
    state: RecordState,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val listening = state as? RecordState.Listening
    // Запись уже остановлена, идёт распознавание: кнопка занята так же, как при разборе
    val recognizing = state is RecordState.Recognizing
    val working = busy || recognizing
    val level by animateFloatAsState(targetValue = listening?.level ?: 0f, label = "micLevel")
    val caption = when {
        busy -> "Разбираю…"
        recognizing -> "Распознаю…"
        listening != null -> listening.partial.ifBlank { "Слушаю… нажмите, когда закончите" }
        state is RecordState.Error -> state.message
        else -> "Нажмите и скажите: «кофе 250 и такси 400»"
    }
    val buttonSize = if (compact) 64 else 96
    val ringExtra = if (compact) 20 else 36
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) TratyTheme.spacing.xs else TratyTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size((buttonSize + ringExtra).dp), contentAlignment = Alignment.Center) {
            if (listening != null) {
                Box(
                    Modifier
                        .size((buttonSize + ringExtra * level).dp)
                        .clip(CircleShape)
                        .background(TratyTheme.colors.primary.copy(alpha = 0.18f)),
                )
            }
            Box(
                modifier = Modifier
                    .size(buttonSize.dp)
                    .shadow(if (compact) 4.dp else 6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(TratyTheme.colors.primary)
                    .clickable(enabled = !working, role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = when {
                            busy -> "Разбираю трату"
                            recognizing -> "Распознаю запись"
                            listening != null -> "Закончить запись"
                            else -> "Сказать трату"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 28.dp else 40.dp),
                        strokeWidth = 3.dp,
                        color = TratyTheme.colors.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (listening != null) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = TratyTheme.colors.onPrimary,
                        modifier = Modifier.size(if (compact) 30.dp else 44.dp),
                    )
                }
            }
        }
        if (!compact) Text(
            text = caption,
            style = TratyTheme.typography.bodyMedium,
            color = if (state is RecordState.Error && !busy) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
        )
    }
}

@Preview(name = "Mic: idle", showBackground = true)
@Composable
private fun MicButtonIdlePreview() {
    TratyTheme(darkTheme = false) { MicButton(RecordState.Idle, busy = false, onClick = {}) }
}

@Preview(name = "Mic: compact", showBackground = true)
@Composable
private fun MicButtonCompactPreview() {
    TratyTheme(darkTheme = false) { MicButton(RecordState.Idle, busy = false, onClick = {}, compact = true) }
}

@Preview(name = "Mic: listening", showBackground = true)
@Composable
private fun MicButtonListeningPreview() {
    TratyTheme(darkTheme = false) {
        MicButton(RecordState.Listening("кофе двести пятьдесят и такси", 0.6f), busy = false, onClick = {})
    }
}
