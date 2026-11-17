package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/**
 * Строка траты и под ней товары, из-за которых она нашлась поиском («шоколад Alpen Gold — 209,97 ₽»).
 * Без поиска [matched] пуст, и строка выглядит как обычно.
 */
@Composable
fun ExpenseRowWithMatches(
    row: ExpenseWithCategory,
    onClick: () -> Unit,
    matched: List<ExpenseItem>,
    modifier: Modifier = Modifier,
    labels: List<ExpenseLabelHit> = emptyList(),
    itemCount: Int = 0,
) {
    Column(modifier) {
        ExpenseRow(row = row, onClick = onClick, labels = labels, itemCount = itemCount)
        MatchedItems(matched)
    }
}

/** Найденные товары с ценой, под текстом строки траты (отступ слева — на ширину значка категории). */
@Composable
fun MatchedItems(items: List<ExpenseItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TratyTheme.spacing.lg + 42.dp + TratyTheme.spacing.md,
                end = TratyTheme.spacing.lg,
                bottom = TratyTheme.spacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = matchedItemTitle(item),
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.priceKopecks?.let(::formatRub) ?: "без цены",
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = TratyTheme.spacing.sm),
                )
            }
        }
    }
}

/** «шоколад Alpen Gold», а если названо количество или фасовка — «молоко · 2 уп», «коктейль · 200 мл». */
internal fun matchedItemTitle(item: ExpenseItem): String {
    val pack = ItemMeasure.packOf("", item.packMilli, item.packUnit)
    val amount = ItemMeasure.describe(item.quantityMilli, item.unit?.takeIf { it.isNotBlank() }, pack)
    return if (amount == null) item.name else "${item.name} · $amount"
}
