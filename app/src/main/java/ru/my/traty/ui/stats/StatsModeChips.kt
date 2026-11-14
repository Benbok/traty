package ru.my.traty.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Переключатель режима статистики: расчётный период, день, неделя или произвольные даты. */
@Composable
fun StatsModeChips(
    selected: StatsSpan,
    onSelect: (StatsSpan) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        StatsSpan.entries.forEach { span ->
            ModeChip(span.title, selected = span == selected, onClick = { onSelect(span) })
        }
    }
}

/** Что показывать списком: категории (с разбивкой по магазинам внутри) или сразу все магазины. */
@Composable
fun StatsViewChips(
    storesSelected: Boolean,
    onCategories: () -> Unit,
    onStores: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        ModeChip("Категории", selected = !storesSelected, onClick = onCategories)
        ModeChip("Магазины", selected = storesSelected, onClick = onStores)
    }
}

@Composable
internal fun ModeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = TratyTheme.shapes.small,
        color = if (selected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
        modifier = Modifier.clickable(onClick = onClick),
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
