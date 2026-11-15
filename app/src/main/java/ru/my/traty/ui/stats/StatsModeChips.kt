package ru.my.traty.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Переключатель режима статистики: день или неделя. Повторный клик по выбранному чипу возвращает к расчётному периоду. */
@Composable
fun StatsModeChips(
    selected: StatsSpan,
    onSelect: (StatsSpan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSpans = remember {
        StatsSpan.entries.filter { it != StatsSpan.PERIOD && it != StatsSpan.DATES }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        visibleSpans.forEach { span ->
            val isSelected = span == selected
            ModeChip(
                span.title,
                selected = isSelected,
                onClick = {
                    if (isSelected) onSelect(StatsSpan.PERIOD) else onSelect(span)
                },
            )
        }
    }
}

@Composable
internal fun ModeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = TratyTheme.shapes.small,
        color = if (selected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
        // Видимый размер чипа прежний, но нажимаемая область не меньше 48dp
        modifier = Modifier.clickable(onClick = onClick).minimumInteractiveComponentSize(),
    ) {
        Text(
            text = title,
            style = TratyTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
