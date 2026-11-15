package ru.my.traty.ui.debt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.debt.DebtTreeNode
import ru.my.traty.data.debt.TreePaymentState
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette
import ru.my.traty.ui.formatDateNumeric
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/** Размеры линии таймлайна и значков (не отступы: отступы — из [TratyTheme.spacing]). */
private val TimelineWidth = 36.dp
private val LineWidth = 2.dp
private val LineToIcon = 12.dp
private val StatusIconSize = 24.dp
private val StatusGlyphSize = 14.dp
private val CardBorder = 1.dp
private val BadgeHorizontalPadding = 6.dp

/** Одна строка дерева: линия с кружком статуса слева, карточка платежа справа. */
@Composable
internal fun DebtTreeNodeRow(
    node: DebtTreeNode,
    isFirst: Boolean,
    isLast: Boolean,
    isLoan: Boolean,
    onOpenExpense: (Long) -> Unit,
    onPayNode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg)
            .height(IntrinsicSize.Min),
    ) {
        TimelineColumn(node.state, isFirst, isLast)
        Spacer(Modifier.width(TratyTheme.spacing.sm))
        NodeCard(node, isLoan, onOpenExpense, onPayNode, Modifier.weight(1f))
    }
}

/** Таймлайн: вертикальная линия и кружок статуса. */
@Composable
private fun TimelineColumn(state: TreePaymentState, isFirst: Boolean, isLast: Boolean) {
    Column(
        modifier = Modifier
            .width(TimelineWidth)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Линия сверху
        if (!isFirst) {
            Box(
                Modifier
                    .width(LineWidth)
                    .height(LineToIcon)
                    .background(TratyTheme.colors.borderSubtle),
            )
        } else {
            Spacer(Modifier.height(LineToIcon))
        }

        TimelineStatusIcon(state)

        // Линия снизу
        if (!isLast) {
            Box(
                Modifier
                    .width(LineWidth)
                    .weight(1f)
                    .background(TratyTheme.colors.borderSubtle),
            )
        } else {
            Spacer(Modifier.height(LineToIcon))
        }
    }
}

/** Карточка платежа: номер и статус, сумма, «Трата ➔» или «Оплатить». */
@Composable
private fun NodeCard(
    node: DebtTreeNode,
    isLoan: Boolean,
    onOpenExpense: (Long) -> Unit,
    onPayNode: () -> Unit,
    modifier: Modifier,
) {
    TratyCard(
        modifier = modifier.padding(vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surface,
        border = BorderStroke(
            width = CardBorder,
            color = if (node.state == TreePaymentState.OVERDUE) TratyPalette.CoralRose.copy(alpha = 0.5f) else TratyTheme.colors.borderSubtle,
        ),
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = nodeTitle(node),
                    style = TratyTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                NodeStatusBadge(node)
            }

            Spacer(Modifier.height(TratyTheme.spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NodeAmount(node, isLoan)
                NodeAction(node, onOpenExpense, onPayNode)
            }
        }
    }
}

private fun nodeTitle(node: DebtTreeNode): String = node.note ?: when {
    node.number != null && node.totalCount != null -> "Платёж ${node.number} из ${node.totalCount}"
    node.number != null -> "Платёж ${node.number}"
    else -> "Платёж"
}

@Composable
private fun NodeAmount(node: DebtTreeNode, isLoan: Boolean) {
    Column {
        TratyAmountText(
            amountKopecks = node.totalKopecks,
            size = AmountSize.MEDIUM,
            fontWeight = FontWeight.Bold,
        )
        if (isLoan && node.interestKopecks > 0) {
            Text(
                text = "Тело: ${formatRub(node.principalKopecks)} · %: ${formatRub(node.interestKopecks)}",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeAction(node: DebtTreeNode, onOpenExpense: (Long) -> Unit, onPayNode: () -> Unit) {
    val expenseId = node.expenseId
    if (node.state == TreePaymentState.PAID && expenseId != null) {
        TratyTextButton(onClick = { onOpenExpense(expenseId) }) {
            Text("Трата ➔", style = TratyTheme.typography.labelSmall)
        }
    } else if (node.state == TreePaymentState.OVERDUE || node.state == TreePaymentState.UPCOMING || node.state == TreePaymentState.PARTIALLY_PAID) {
        TratyOutlinedButton(onClick = onPayNode) {
            Text("Оплатить", style = TratyTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TimelineStatusIcon(state: TreePaymentState) {
    val (bg, iconColor, icon) = when (state) {
        TreePaymentState.PAID -> Triple(TratyPalette.Emerald500, Color.White, Icons.Filled.Check)
        TreePaymentState.OVERDUE -> Triple(TratyPalette.CoralRose, Color.White, Icons.Filled.ErrorOutline)
        TreePaymentState.PARTIALLY_PAID -> Triple(TratyPalette.AmberWarning, Color.White, Icons.Filled.Check)
        TreePaymentState.UPCOMING -> Triple(TratyTheme.colors.surfaceContainerHigh, TratyTheme.colors.onSurfaceVariant, Icons.Filled.Schedule)
    }

    Box(
        modifier = Modifier
            .size(StatusIconSize)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(StatusGlyphSize),
        )
    }
}

@Composable
private fun NodeStatusBadge(node: DebtTreeNode) {
    val (bg, text, label) = when (node.state) {
        TreePaymentState.PAID -> {
            val date = node.actualDate?.let { formatDateNumeric(it) }
            val textStr = if (date != null) "Оплачен $date" else "Оплачен"
            Triple(TratyPalette.Emerald50, TratyPalette.Emerald700, textStr)
        }
        TreePaymentState.OVERDUE -> {
            val textStr = if (node.daysOverdue > 0) {
                "Просрочен на ${node.daysOverdue} ${pluralRu(node.daysOverdue, "день", "дня", "дней")}"
            } else {
                "Просрочен"
            }
            Triple(TratyPalette.CoralRoseLight, TratyPalette.CoralRose700, textStr)
        }
        TreePaymentState.PARTIALLY_PAID -> {
            Triple(TratyPalette.AmberLight, TratyPalette.Amber700, "Внесено ${formatRub(node.paidAmountKopecks)}")
        }
        TreePaymentState.UPCOMING -> {
            val date = node.scheduledDate?.let { formatDateNumeric(it) } ?: "По графику"
            Triple(TratyTheme.colors.surfaceContainerHigh, TratyTheme.colors.onSurfaceVariant, "Срок: $date")
        }
    }

    Surface(
        shape = TratyTheme.shapes.small,
        color = bg,
    ) {
        Text(
            text = label,
            style = TratyTheme.typography.labelSmall,
            color = text,
            modifier = Modifier.padding(horizontal = BadgeHorizontalPadding, vertical = TratyTheme.spacing.xxs),
        )
    }
}
