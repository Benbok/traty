package ru.my.traty.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.Places
import ru.my.traty.data.items.ItemSale
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub
import java.time.Instant
import java.time.ZoneId

/** «Телефон: где и почём» — каждая покупка товара за выбранные даты, новые сверху. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemHistorySheet(product: ProductTotal, history: List<ItemSale>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.padding(horizontal = TratyTheme.spacing.lg)) {
            Text(product.name, style = TratyTheme.typography.titleMedium)
            Text(
                "${formatRub(product.totalKopecks)} · ${ItemTexts.subtitle(product, showPlaces = false)}",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            items(history, key = { "${it.expenseId}-${it.name}-${it.priceKopecks}" }) { sale -> HistoryRow(sale) }
        }
    }
}

@Composable
private fun HistoryRow(sale: ItemSale) {
    val day = Instant.ofEpochMilli(sale.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Text(formatDate(day), style = TratyTheme.typography.bodyMedium)
            val details = listOfNotNull(Places.normalize(sale.place), ItemTexts.quantity(sale.quantityMilli, sale.unit))
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
        Text(
            sale.priceKopecks?.let(::formatRub) ?: "без цены",
            style = TratyTheme.typography.bodyMedium,
            color = if (sale.priceKopecks == null) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
        )
    }
}
