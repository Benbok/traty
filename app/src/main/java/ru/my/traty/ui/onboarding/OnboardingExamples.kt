package ru.my.traty.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

// Маленькие «живые» примеры для карточек знакомства. Это не настоящие данные человека.

@Composable
internal fun ExampleChip(text: String) {
    Surface(shape = TratyTheme.shapes.extraLarge, color = TratyTheme.colors.surface) {
        Text(
            text,
            style = TratyTheme.typography.labelMedium,
            color = TratyTheme.colors.primary,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
        )
    }
}

/** Пузырь реплики: свой (справа, цветной) или ответ (слева, белый). */
@Composable
internal fun ExampleBubble(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = TratyTheme.shapes.medium,
        color = if (mine) TratyTheme.colors.primary else TratyTheme.colors.surface,
        modifier = modifier,
    ) {
        Text(
            text,
            style = TratyTheme.typography.bodyMedium,
            color = if (mine) TratyTheme.colors.onPrimary else TratyTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
        )
    }
}

/** Круглая кнопка микрофона как на ленте, только картинка. */
@Composable
internal fun ExampleMic(size: Dp, shape: Shape = CircleShape) {
    Surface(shape = shape, color = TratyTheme.colors.primary, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = TratyTheme.colors.onPrimary)
        }
    }
}

/** Строка траты: значок категории, название, что купили, сумма. */
@Composable
internal fun ExampleExpenseRow(emoji: String, category: String, note: String, kopecks: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        TratyCategoryBadge(emoji = emoji, categoryName = category, size = TratyTheme.spacing.huge)
        Column(Modifier.weight(1f)) {
            Text(category, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
            Text(note, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
        }
        Text("−${formatRub(kopecks)}", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.expense)
    }
}

/** Строка плана: категория, «потрачено из плана» и полоска. Перерасход подсвечивается. */
@Composable
internal fun ExamplePlanRow(category: String, spentKopecks: Long, planKopecks: Long) {
    val over = spentKopecks > planKopecks
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(category, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
            Text(
                "${formatRub(spentKopecks)} из ${formatRub(planKopecks)}",
                style = TratyTheme.typography.bodySmall,
                color = if (over) TratyTheme.colors.warning else TratyTheme.colors.onSurfaceVariant,
            )
        }
        TratyProgressBar(
            progress = (spentKopecks.toFloat() / planKopecks).coerceIn(0f, 1f),
            color = if (over) TratyTheme.colors.warning else TratyTheme.colors.primary,
        )
    }
}

/** Белая подложка для группы примеров внутри цветной картинки. */
@Composable
internal fun ExampleSurface(content: @Composable () -> Unit) {
    Surface(shape = TratyTheme.shapes.medium, color = TratyTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) { content() }
    }
}
