package ru.my.traty.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ru.my.traty.data.labels.ExpenseLabel
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ExpenseLabelChip(label: ExpenseLabel, fullTitle: Boolean = false) {
    val colors = TratyTheme.colors
    val tint = when (label) {
        ExpenseLabel.IMPULSIVE -> colors.warning
        ExpenseLabel.LARGE -> colors.expense
        else -> colors.onSurfaceVariant
    }
    val icon = when (label) {
        ExpenseLabel.IMPULSIVE -> Icons.Outlined.Bolt
        ExpenseLabel.LARGE -> Icons.AutoMirrored.Outlined.TrendingUp
        ExpenseLabel.FREQUENT -> Icons.Outlined.Repeat
        ExpenseLabel.SUBSCRIPTION -> Icons.Outlined.Event
        ExpenseLabel.NEW_PLACE -> Icons.Outlined.Storefront
    }
    val title = if (fullTitle) label.title else when (label) {
        ExpenseLabel.LARGE -> "Крупная"
        ExpenseLabel.SUBSCRIPTION -> "Подписка?"
        else -> label.title
    }
    Row(
        modifier = Modifier
            .clip(TratyTheme.shapes.small)
            .background(
                if (label == ExpenseLabel.IMPULSIVE || label == ExpenseLabel.LARGE) {
                    tint.copy(alpha = if (colors.isDark) 0.16f else 0.10f)
                } else colors.surfaceContainer,
            )
            .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(TratyTheme.spacing.lg))
        Text(title, style = TratyTheme.typography.labelSmall, color = tint)
    }
}
