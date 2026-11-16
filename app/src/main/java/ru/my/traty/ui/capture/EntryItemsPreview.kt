package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.capture.DraftItem
import ru.my.traty.data.items.ItemsBalance
import ru.my.traty.data.items.ItemsBalanceState
import ru.my.traty.data.items.ItemsCheck
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/**
 * Товары в окне проверки: «Телефон — 40 000 ₽», сверка с суммой траты.
 * Крестик убирает товар; цены и названия правятся потом на экране траты.
 */
@Composable
fun EntryItemsPreview(
    items: List<DraftItem>,
    totalKopecks: Long?,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs)) {
        Text("Товары (${items.size})", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
        items.forEachIndexed { i, item -> ItemLine(item, onRemove = { onRemove(i) }) }
        if (totalKopecks != null) {
            BalanceLine(ItemsCheck.balance(totalKopecks, items.map { it.priceKopecks() }))
        }
    }
}

@Composable
private fun ItemLine(item: DraftItem, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val qty = listOf(item.qtyText, item.unit).filter { it.isNotBlank() }.joinToString(" ")
        Text(
            if (qty.isEmpty()) item.name else "${item.name}, $qty",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            item.priceKopecks()?.let(::formatRub) ?: "без цены",
            style = TratyTheme.typography.bodyMedium,
            color = if (item.priceKopecks() == null) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, "Убрать товар", tint = TratyTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BalanceLine(b: ItemsBalance, modifier: Modifier = Modifier) {
    val (text, color) = balanceText(b)
    if (text.isEmpty()) return
    Text(text, style = TratyTheme.typography.bodySmall, color = color, modifier = modifier)
}

@Composable
private fun balanceText(b: ItemsBalance): Pair<String, Color> = when (b.state) {
    ItemsBalanceState.NO_PRICES -> "" to TratyTheme.colors.onSurfaceVariant
    ItemsBalanceState.MATCH -> "Цены товаров сходятся с суммой" to TratyTheme.colors.onSurfaceVariant
    ItemsBalanceState.REST ->
        (if (b.unpriced > 0) "На товары без цены: ${formatRub(b.differenceKopecks)}" else "Не разложено по товарам: ${formatRub(b.differenceKopecks)}") to
            TratyTheme.colors.onSurfaceVariant
    ItemsBalanceState.OVER -> "Цены товаров больше суммы на ${formatRub(-b.differenceKopecks)}" to TratyTheme.colors.expense
}
