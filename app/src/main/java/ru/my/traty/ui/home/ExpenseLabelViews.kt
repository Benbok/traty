package ru.my.traty.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Сколько значков показывать в строке траты; остальные видны в карточке траты. */
private const val MAX_BADGES = 3

/** Маленькие значки меток в строке траты. Цвета берутся из темы, поэтому одинаково читаются в светлой и тёмной. */
@Composable
fun ExpenseLabelBadges(labels: List<ExpenseLabelHit>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        labels.take(MAX_BADGES).forEach { hit ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TratyTheme.colors.surfaceContainerHigh)
                    .semantics { contentDescription = hit.label.title },
                contentAlignment = Alignment.Center,
            ) {
                Text(hit.label.emoji, fontSize = 12.sp)
            }
        }
        if (labels.size > MAX_BADGES) {
            Text(
                "+${labels.size - MAX_BADGES}",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Блок «Почему такие метки» в карточке траты: значок, название и пояснение для каждой метки. */
@Composable
fun ExpenseLabelExplanations(labels: List<ExpenseLabelHit>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TratyTheme.shapes.small)
            .background(TratyTheme.colors.surfaceContainer)
            .padding(TratyTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        Text("Метки этой траты", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
        labels.forEach { hit ->
            Row(verticalAlignment = Alignment.Top) {
                Text(hit.label.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Column {
                    Text(hit.label.title, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
                    Spacer(Modifier.height(TratyTheme.spacing.xxs))
                    Text(hit.reason, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                }
            }
        }
    }
}
