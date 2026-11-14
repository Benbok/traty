package ru.my.traty.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.data.TemplateWithCategory
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Горизонтальная полоса чипсов-шаблонов над строкой ввода.
 * Одно нажатие — трата мгновенно сохраняется.
 */
@Composable
fun TemplateChips(
    templates: List<TemplateWithCategory>,
    onUse: (TemplateWithCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        for (t in templates) {
            val label = "${t.categoryEmoji} ${t.template.title} ${t.template.amountKopecks / 100} ₽"
            AssistChip(
                onClick = { onUse(t) },
                label = { Text(label, style = TratyTheme.typography.labelMedium) },
                shape = TratyTheme.shapes.small,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = TratyTheme.colors.surfaceContainer,
                    labelColor = TratyTheme.colors.onSurface,
                ),
                border = BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
            )
        }
    }
}
