package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.capture.DuplicateHint
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * «Похоже, это уже записано»: строка совпала с тратой из базы.
 * «Это повтор» убирает строку, «Новая покупка» оставляет её и убирает вопрос.
 */
@Composable
fun DuplicateNotice(hint: DuplicateHint, onRepeat: () -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.expenseContainer,
        border = null,
    ) {
        Column(
            Modifier.padding(TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
        ) {
            Text("Похоже, это уже записано", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
            Text(
                "«${hint.label}», ${formatRub(hint.amountKopecks)}, ${whenText(hint.timestamp)}. " +
                    if (hint.income) "Это то же пополнение или новое?" else "Это та же покупка или новая?",
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                TratyTextButton(onClick = onRepeat) { Text("Это повтор") }
                TratyButton(onClick = onNew) { Text(if (hint.income) "Новое пополнение" else "Новая покупка") }
            }
        }
    }
}

/** «сегодня в 14:05», «вчера в 9:30», «12 ноября 2026 в 18:00». */
private fun whenText(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val dayText = when (day) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> formatDate(day)
    }
    return "$dayText в ${formatTime(millis)}"
}
