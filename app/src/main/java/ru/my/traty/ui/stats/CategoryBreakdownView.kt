package ru.my.traty.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import ru.my.traty.data.BreakdownGroup
import ru.my.traty.data.BreakdownMode
import ru.my.traty.data.CategoryBreakdown
import ru.my.traty.data.CategoryBreakdowns
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import kotlin.math.roundToInt
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatTime
import ru.my.traty.ui.home.ExpenseLabelBadges

internal fun groupKey(categoryId: Long, mode: BreakdownMode, g: BreakdownGroup) = "$categoryId/${mode.name}/${g.key}"

/** Сколько трат показывать в раскрытом списке, остальное — строкой «и ещё N». */
private const val EXPENSES_SHOWN = 30

/**
 * Состав раскрытой категории:
 * - Компактный сегментированный переключатель «Подкатегории / Магазины / Траты».
 * - Кнопка «Товары ›», открывающая аналитику покупок по чекам.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryBreakdownView(
    breakdown: CategoryBreakdown,
    mode: BreakdownMode,
    onMode: (BreakdownMode) -> Unit,
    isGroupExpanded: (BreakdownGroup) -> Boolean,
    onToggleGroup: (BreakdownGroup) -> Unit,
    onOpenExpense: (ExpenseWithCategory) -> Unit,
    labels: Map<Long, List<ExpenseLabelHit>>,
    hasItems: Boolean = false,
    onOpenItems: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(start = 48.dp)) {
        if (breakdown.modes.size > 1 || hasItems) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TratyTheme.spacing.xs),
            ) {
                if (breakdown.modes.size > 1) {
                    CategorySegmentedSwitch(
                        modes = breakdown.modes,
                        selected = mode,
                        onSelect = onMode,
                    )
                }
                if (hasItems) {
                    CategoryItemsButton(onClick = onOpenItems)
                }
            }
        }
        if (mode == BreakdownMode.EXPENSES) {
            ExpenseList(breakdown.expenses, mode, onOpenExpense, labels)
        } else {
            breakdown.groups(mode).forEach { g ->
                val open = isGroupExpanded(g)
                BreakdownGroupRow(group = g, categoryTotal = breakdown.total, expanded = open, onClick = { onToggleGroup(g) })
                if (open) Column(Modifier.padding(start = TratyTheme.spacing.md)) { ExpenseList(g.expenses, mode, onOpenExpense, labels) }
            }
        }
    }
}

/** Компактный сегментированный переключатель режимов: Подкатегории / Магазины / Траты. */
@Composable
private fun CategorySegmentedSwitch(
    modes: List<BreakdownMode>,
    selected: BreakdownMode,
    onSelect: (BreakdownMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = TratyTheme.shapes.small,
        color = TratyTheme.colors.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(TratyTheme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEach { m ->
                val isSelected = m == selected
                Surface(
                    shape = TratyTheme.shapes.extraSmall,
                    color = if (isSelected) TratyTheme.colors.primaryContainer else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.primary.copy(alpha = 0.25f)) else null,
                    modifier = Modifier
                        .clip(TratyTheme.shapes.extraSmall)
                        .clickable { onSelect(m) },
                ) {
                    Text(
                        text = m.title,
                        style = TratyTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = TratyTheme.spacing.sm,
                            vertical = TratyTheme.spacing.xs,
                        ),
                    )
                }
            }
        }
    }
}

/** Кнопка открытия аналитики товаров категории со стрелкой перехода. */
@Composable
private fun CategoryItemsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = TratyTheme.shapes.small,
        color = TratyTheme.colors.surfaceContainer,
        border = BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
        modifier = modifier
            .clip(TratyTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = TratyTheme.spacing.sm,
                vertical = TratyTheme.spacing.xs,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = TratyTheme.colors.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(TratyTheme.spacing.xs))
            Text(
                text = "Товары",
                style = TratyTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = TratyTheme.colors.onSurface,
            )
            Spacer(Modifier.width(TratyTheme.spacing.xxs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Подкатегория или магазин внутри раскрытой категории: название, число трат, сумма и доля в категории. */
@Composable
private fun BreakdownGroupRow(group: BreakdownGroup, categoryTotal: Long, expanded: Boolean, onClick: () -> Unit) {
    val frac = if (categoryTotal > 0) group.total.toFloat() / categoryTotal else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = TratyTheme.spacing.xxs, bottom = TratyTheme.spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = TratyTheme.typography.bodyMedium,
                    color = if (group.isUnassigned) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
                )
                Text(
                    text = "${purchases(group.count)} · ${(frac * 100).roundToInt()}%",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            TratyAmountText(amountKopecks = group.total, size = AmountSize.SMALL, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Скрыть траты" else "Показать траты",
                tint = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = TratyTheme.spacing.xs).size(18.dp),
            )
        }
        TratyProgressBar(progress = frac, height = 4.dp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Траты списком: что купили, когда и где, сумма. */
@Composable
private fun ExpenseList(
    list: List<ExpenseWithCategory>,
    mode: BreakdownMode,
    onOpenExpense: (ExpenseWithCategory) -> Unit,
    labels: Map<Long, List<ExpenseLabelHit>>,
) {
    list.take(EXPENSES_SHOWN).forEach { e ->
        BreakdownExpenseRow(e, showPlace = mode != BreakdownMode.STORES, labels = labels[e.expense.id].orEmpty(), modifier = Modifier.clickable { onOpenExpense(e) })
    }
    if (list.size > EXPENSES_SHOWN) {
        Text(
            text = "и ещё ${purchases(list.size - EXPENSES_SHOWN)} — все траты на главной",
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs),
        )
    }
}

@Composable
private fun BreakdownExpenseRow(
    e: ExpenseWithCategory,
    showPlace: Boolean,
    labels: List<ExpenseLabelHit>,
    modifier: Modifier = Modifier,
) {
    val title = CategoryBreakdowns.title(e).ifEmpty { "Без описания" }
    val place = Places.normalize(e.expense.place)?.takeIf { showPlace && it != title }
    Row(
        modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurface, maxLines = 1)
            if (labels.isNotEmpty()) {
                ExpenseLabelBadges(labels, Modifier.padding(vertical = TratyTheme.spacing.xxs))
            }
            Text(
                text = listOfNotNull(formatTime(e.expense.timestamp), place).joinToString(" · "),
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TratyAmountText(amountKopecks = e.expense.amountKopecks, size = AmountSize.SMALL)
    }
}
