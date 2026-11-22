package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/**
 * Найденное поиском в других периодах, под лентой открытого периода: плашка
 * «За этот период ничего не нашлось» (или «Ещё нашлось»), затем периоды с датами и сами записи.
 */
fun LazyListScope.searchElsewhere(
    groups: List<PeriodMatches>,
    startDay: Int,
    currentEmpty: Boolean,
    onExpenseClick: (ExpenseWithCategory) -> Unit,
    onIncomeClick: (Income) -> Unit,
    /** Товары покупок и запрос: показать под найденной тратой нужные товары. */
    itemsByExpense: Map<Long, List<ExpenseItem>> = emptyMap(),
    query: String = "",
    itemCounts: Map<Long, Int> = emptyMap(),
    labels: Map<Long, List<ru.my.traty.data.labels.ExpenseLabelHit>> = emptyMap(),
) {
    if (groups.isEmpty()) return
    item(key = "elsewhere-banner", contentType = "elsewhere-banner") {
        ElsewhereBanner(count = groups.sumOf { it.count }, currentEmpty = currentEmpty)
    }
    groups.forEach { g ->
        item(key = "elsewhere-${g.period.startDate}", contentType = "elsewhere-period") {
            ElsewherePeriodHeader(g, startDay)
        }
        items(buildFeed(g.expenses, g.incomes), key = { "elsewhere-${it.id}" }, contentType = { it.contentType }) { item ->
            when (item) {
                is FeedItem.ExpenseItem -> ExpenseRowWithMatches(
                    row = item.data,
                    onClick = { onExpenseClick(item.data) },
                    matched = FeedSearch.matchedItems(itemsByExpense[item.data.expense.id].orEmpty(), query),
                    itemCount = itemCounts[item.data.expense.id] ?: 0,
                    labels = labels[item.data.expense.id].orEmpty(),
                )
                is FeedItem.IncomeItem -> IncomeRow(income = item.data, onClick = { onIncomeClick(item.data) })
            }
            HorizontalDivider(
                color = TratyTheme.colors.borderSubtle,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
            )
        }
    }
}

@Composable
private fun ElsewhereBanner(count: Int, currentEmpty: Boolean) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        backgroundColor = TratyTheme.colors.primaryContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text(
                text = if (currentEmpty) "За этот период ничего не нашлось" else "Ещё нашлось в других периодах",
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.primary,
            )
            val records = pluralRu(count.toLong(), "запись", "записи", "записей")
            Text(
                text = if (currentEmpty) "Зато в других периодах: $count $records" else "$count $records ниже",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ElsewherePeriodHeader(g: PeriodMatches, startDay: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, top = TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatPeriod(g.period, startDay),
            style = TratyTheme.typography.labelLarge,
            color = TratyTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (g.expenses.isNotEmpty()) {
            val n = g.expenses.size.toLong()
            Text(
                text = "$n ${pluralRu(n, "трата", "траты", "трат")} · ${formatRub(g.expenseTotal)}",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
