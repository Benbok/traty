package ru.my.traty.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.data.debt.DebtTreeSummary
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/** Размеры элементов карточки (не отступы: отступы — из [TratyTheme.spacing]). */
private val BadgeSize = 38.dp
private val ButtonIconSize = 18.dp
private val ChipVerticalPadding = 3.dp
private val ProgressHeight = 8.dp

/** Карточка сводки: категория и статус, прогресс, «Оплачено / Осталось / Просрочено», «Внести платёж». */
@Composable
internal fun DebtSummaryCard(
    debt: Debt,
    summary: DebtTreeSummary,
    catEmoji: String,
    catName: String,
    onPayClick: () -> Unit,
) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            SummaryHeader(debt, catEmoji, catName)
            Spacer(Modifier.height(TratyTheme.spacing.md))
            SummaryProgress(debt, summary)
            Spacer(Modifier.height(TratyTheme.spacing.md))
            SummaryMetrics(debt, summary)

            // Кнопка внесения платежа (если активна и не закрыта)
            if (debt.state == DebtState.ACTIVE && !summary.isPaidOff) {
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPayClick,
                ) {
                    Icon(Icons.Filled.Payment, null, modifier = Modifier.size(ButtonIconSize))
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Text("Внести платёж")
                }
            }
        }
    }
}

/** Категория, место и бейдж статуса. */
@Composable
private fun SummaryHeader(debt: Debt, catEmoji: String, catName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(
            emoji = catEmoji,
            categoryName = catName,
            size = BadgeSize,
        )
        Spacer(Modifier.width(TratyTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(catName, debt.place).joinToString(" · "),
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = debt.title,
                style = TratyTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        StateChip(debt.state)
    }
}

@Composable
private fun SummaryProgress(debt: Debt, summary: DebtTreeSummary) {
    val barColor = when {
        summary.isPaidOff -> TratyTheme.colors.primary
        summary.overdueCount > 0 -> TratyPalette.CoralRose
        else -> TratyTheme.colors.primary
    }
    TratyProgressBar(
        progress = summary.progressPercent,
        color = barColor,
        height = ProgressHeight,
    )

    Spacer(Modifier.height(TratyTheme.spacing.xs))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${(summary.progressPercent * 100).toInt()}% оплачено",
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        val countText = if (debt.paymentsCount != null) {
            "${summary.paidCount} из ${debt.paymentsCount} платежей"
        } else {
            "${summary.paidCount} ${pluralRu(summary.paidCount.toLong(), "платёж", "платежа", "платежей")}"
        }
        Text(
            text = countText,
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryMetrics(debt: Debt, summary: DebtTreeSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        MetricBox(
            label = "Оплачено",
            value = formatRub(summary.paidKopecks),
            color = TratyTheme.colors.primary,
            modifier = Modifier.weight(1f),
        )
        MetricBox(
            label = "Осталось",
            value = summary.remainingKopecks?.let { formatRub(it) } ?: "—",
            color = TratyTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (summary.overdueKopecks > 0) {
            MetricBox(
                label = "Просрочено",
                value = formatRub(summary.overdueKopecks),
                color = TratyPalette.CoralRose,
                modifier = Modifier.weight(1f),
            )
        } else if (debt.debtKind == DebtKind.LOAN && summary.plannedOverpaymentKopecks > 0) {
            MetricBox(
                label = "Проценты",
                value = formatRub(summary.plannedOverpaymentKopecks),
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = TratyTheme.shapes.medium,
        color = TratyTheme.colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = TratyTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StateChip(state: String) {
    val (text, color) = when (state) {
        DebtState.ACTIVE -> "Активна" to TratyTheme.colors.primary
        DebtState.CLOSED -> "Закрыта" to TratyTheme.colors.onSurfaceVariant
        DebtState.CANCELLED -> "Отменена" to TratyPalette.CoralRose
        else -> state to TratyTheme.colors.onSurfaceVariant
    }
    Surface(
        shape = TratyTheme.shapes.pill,
        color = TratyTheme.colors.surfaceContainer,
    ) {
        Text(
            text = text,
            style = TratyTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = ChipVerticalPadding),
        )
    }
}
