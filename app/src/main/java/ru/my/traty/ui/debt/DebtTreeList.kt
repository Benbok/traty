package ru.my.traty.ui.debt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtTree
import ru.my.traty.data.debt.DebtTreeNode
import ru.my.traty.data.debt.DebtTreeSummary
import ru.my.traty.data.debt.TreePaymentState
import ru.my.traty.ui.designsystem.components.TratyFilterChip
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette

internal enum class TreeFilter {
    ALL,
    OVERDUE,
    UPCOMING,
    PAID,
}

private val DebtTreeNode.isOpen: Boolean
    get() = state == TreePaymentState.OVERDUE || state == TreePaymentState.UPCOMING || state == TreePaymentState.PARTIALLY_PAID

private fun List<DebtTreeNode>.filtered(by: TreeFilter): List<DebtTreeNode> = when (by) {
    TreeFilter.ALL -> this
    TreeFilter.OVERDUE -> filter { it.state == TreePaymentState.OVERDUE }
    TreeFilter.UPCOMING -> filter { it.state == TreePaymentState.UPCOMING || it.state == TreePaymentState.PARTIALLY_PAID }
    TreeFilter.PAID -> filter { it.state == TreePaymentState.PAID }
}

/**
 * Сводка, фильтры и строки дерева платежей.
 * [onPay] получает сумму, которую подставить в «Внести платёж».
 */
@Composable
internal fun DebtTreeList(
    debt: Debt,
    tree: DebtTree,
    category: CategoryWithSubs?,
    filter: TreeFilter,
    onFilter: (TreeFilter) -> Unit,
    onPay: (Long) -> Unit,
    onOpenExpense: (Long) -> Unit,
    pad: PaddingValues,
) {
    val summary = tree.summary
    val filteredNodes = remember(tree.nodes, filter) { tree.nodes.filtered(filter) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
    ) {
        // Карточка сводки покупки
        item(key = "summary") {
            DebtSummaryCard(
                debt = debt,
                summary = summary,
                catEmoji = category?.category?.emoji ?: "📦",
                catName = category?.category?.name ?: "Покупка",
                onPayClick = {
                    val next = tree.nodes.firstOrNull { it.isOpen }
                    onPay(next?.remainingKopecks ?: summary.remainingKopecks ?: 0L)
                },
            )
        }

        // Фильтры дерева платежей
        item(key = "filters") {
            TreeFilterChips(summary = summary, selected = filter, onSelect = onFilter)
        }

        // Узлы дерева платежей
        if (filteredNodes.isEmpty()) {
            item(key = "empty_filter") { EmptyFilterText() }
        } else {
            items(filteredNodes, key = { it.id }) { node ->
                DebtTreeNodeRow(
                    node = node,
                    isFirst = node == filteredNodes.first(),
                    isLast = node == filteredNodes.last(),
                    isLoan = debt.debtKind == DebtKind.LOAN,
                    onOpenExpense = onOpenExpense,
                    onPayNode = { onPay(node.remainingKopecks) },
                )
            }
        }

        item(key = "bottom_space") {
            Spacer(Modifier.height(TratyTheme.spacing.xxl))
        }
    }
}

@Composable
private fun EmptyFilterText() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = TratyTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Нет платежей в этой группе",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TreeFilterChips(
    summary: DebtTreeSummary,
    selected: TreeFilter,
    onSelect: (TreeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        TreeChip(TreeFilter.ALL, selected, "Все (${summary.totalCount})", onSelect)
        if (summary.overdueCount > 0) {
            TreeChip(TreeFilter.OVERDUE, selected, "🔴 Просрочено (${summary.overdueCount})", onSelect, overdue = true)
        }
        TreeChip(TreeFilter.UPCOMING, selected, "⏳ Предстоит (${summary.upcomingCount})", onSelect)
        TreeChip(TreeFilter.PAID, selected, "✅ Оплачено (${summary.paidCount})", onSelect)
    }
}

@Composable
private fun TreeChip(
    value: TreeFilter,
    selected: TreeFilter,
    label: String,
    onSelect: (TreeFilter) -> Unit,
    overdue: Boolean = false,
) {
    TratyFilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (overdue) TratyPalette.CoralRoseLight else TratyTheme.colors.primaryContainer,
            selectedLabelColor = if (overdue) TratyPalette.CoralRose700 else TratyTheme.colors.primary,
        ),
    )
}
