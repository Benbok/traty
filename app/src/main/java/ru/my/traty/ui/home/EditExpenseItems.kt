package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.capture.DraftItem
import ru.my.traty.data.items.ItemsCheck
import ru.my.traty.ui.capture.BalanceLine
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/**
 * Карточка «Товары» на экране траты: из чего состоит покупка («Телефон — 40 000 ₽»), сверка с суммой.
 * Нажатие на товар открывает правку в шторке, «Добавить товар» — пустую шторку.
 */
@Composable
internal fun ExpenseItemsCard(
    items: List<DraftItem>,
    totalKopecks: Long?,
    enabled: Boolean,
    onChange: (List<DraftItem>) -> Unit,
) {
    // null — шторка закрыта, -1 — новый товар, иначе номер товара
    var editing by remember { mutableStateOf<Int?>(null) }
    Text(
        if (items.isEmpty()) "Товары" else "Товары (${items.size})",
        style = TratyTheme.typography.labelLarge,
        color = TratyTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
    )
    TratyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (items.isEmpty()) {
                Text(
                    "Разложите покупку по товарам, чтобы видеть траты на каждый товар в статистике",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(TratyTheme.spacing.lg),
                )
            }
            items.forEachIndexed { i, item ->
                if (i > 0) RowDivider()
                ItemRow(item, enabled) { editing = i }
            }
            if (totalKopecks != null && items.isNotEmpty()) {
                BalanceLine(
                    ItemsCheck.balance(totalKopecks, items.map { it.priceKopecks() }),
                    Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
                )
            }
            if (enabled) {
                TratyTextButton(onClick = { editing = -1 }, modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm)) {
                    Text("+ Добавить товар")
                }
            }
        }
    }
    editing?.let { index ->
        ItemEditSheet(
            initial = items.getOrNull(index) ?: DraftItem(""),
            isNew = index < 0,
            onDismiss = { editing = null },
            onDone = { edited ->
                onChange(if (index < 0) items + edited else items.toMutableList().also { it[index] = edited })
                editing = null
            },
            onDelete = {
                onChange(items.filterIndexed { j, _ -> j != index })
                editing = null
            },
        )
    }
}

@Composable
private fun ItemRow(item: DraftItem, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurface)
            val qty = listOf(item.qtyText, item.unit).filter { it.isNotBlank() }.joinToString(" ")
            if (qty.isNotEmpty()) {
                Text(qty, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
        val price = item.priceKopecks()
        Text(
            price?.let(::formatRub) ?: "без цены",
            style = TratyTheme.typography.bodyMedium,
            color = if (price == null) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
        )
    }
}
