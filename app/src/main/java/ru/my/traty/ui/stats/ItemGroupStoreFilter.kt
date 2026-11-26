package ru.my.traty.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.items.ItemGroupSale
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

internal const val NO_STORE_KEY = "__NO_STORE__"

internal data class StoreOption(
    val key: String,
    val place: String?,
    val displayName: String,
    val count: Int,
    val minPriceKopecks: Long?,
    val maxPriceKopecks: Long?,
)

internal object StoreFilterOptions {
    fun build(sales: List<ItemGroupSale>): List<StoreOption> {
        val byPlace = sales.groupBy { it.place?.trim()?.ifEmpty { null } }
        return byPlace.map { (place, storeSales) ->
            val prices = storeSales.mapNotNull { it.unitPriceKopecks ?: it.totalPriceKopecks }
            val minPrice = prices.minOrNull()
            val maxPrice = prices.maxOrNull()
            StoreOption(
                key = place ?: NO_STORE_KEY,
                place = place,
                displayName = place ?: "Без магазина",
                count = storeSales.size,
                minPriceKopecks = minPrice,
                maxPriceKopecks = maxPrice,
            )
        }.sortedWith(
            compareByDescending<StoreOption> { it.count }
                .thenBy { it.displayName },
        )
    }

    /**
     * Возвращает ключи магазинов с минимальной ценой за единицу/позицию.
     * Возвращает пустое множество, если магазинов с известной ценой меньше 2 (сравнивать не с чем).
     */
    fun findBestStoreKeys(options: List<StoreOption>): Set<String> {
        val withPrices = options.filter { it.minPriceKopecks != null }
        if (withPrices.size < 2) return emptySet()
        val min = withPrices.minOf { it.minPriceKopecks ?: Long.MAX_VALUE }
        return withPrices.filter { it.minPriceKopecks == min }.map { it.key }.toSet()
    }
}

/**
 * Ряд чипов магазинов для фильтрации покупок в детализации товара.
 * Самый выгодный магазин (с минимальной ценой) выделяется цветом дохода/экономии и значком.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoreFilterChipsRow(
    stores: List<StoreOption>,
    totalSalesCount: Int,
    selectedKey: String?,
    onSelectKey: (String?) -> Unit,
    bestStoreKeys: Set<String>,
    priceUnit: String?,
    modifier: Modifier = Modifier,
) {
    val unitSuffix = priceUnit?.let { "/$it" }.orEmpty()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        modifier = modifier,
    ) {
        StoreAllFilterChip(
            count = totalSalesCount,
            isSelected = selectedKey == null,
            onClick = { onSelectKey(null) },
        )
        stores.forEach { store ->
            StoreFilterChip(
                store = store,
                unitSuffix = unitSuffix,
                isSelected = selectedKey == store.key,
                isBest = store.key in bestStoreKeys,
                onClick = {
                    onSelectKey(if (selectedKey == store.key) null else store.key)
                },
            )
        }
    }
}

@Composable
private fun StoreAllFilterChip(
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = TratyTheme.shapes.small,
        color = if (isSelected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
        border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.primary) else BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
        modifier = Modifier
            .clip(TratyTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "Все ($count)",
            style = TratyTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
        )
    }
}

@Composable
private fun StoreFilterChip(
    store: StoreOption,
    unitSuffix: String,
    isSelected: Boolean,
    isBest: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        isSelected -> TratyTheme.colors.primaryContainer
        isBest -> TratyTheme.colors.incomeContainer
        else -> TratyTheme.colors.surfaceContainer
    }
    val borderColor = when {
        isSelected -> TratyTheme.colors.primary
        isBest -> TratyTheme.colors.income.copy(alpha = 0.6f)
        else -> TratyTheme.colors.borderSubtle
    }
    val textColor = when {
        isSelected -> TratyTheme.colors.primary
        isBest -> TratyTheme.colors.income
        else -> TratyTheme.colors.onSurface
    }
    val priceText = when {
        store.minPriceKopecks == null -> null
        store.maxPriceKopecks != null && store.minPriceKopecks < store.maxPriceKopecks -> "от ${formatRub(store.minPriceKopecks)}$unitSuffix"
        else -> "${formatRub(store.minPriceKopecks)}$unitSuffix"
    }

    Surface(
        shape = TratyTheme.shapes.small,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(TratyTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
        ) {
            if (isBest) {
                Icon(
                    imageVector = Icons.Filled.Savings,
                    contentDescription = "Самый выгодный",
                    tint = textColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = store.displayName,
                style = TratyTheme.typography.labelMedium,
                fontWeight = if (isSelected || isBest) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
            if (priceText != null) {
                Text(
                    text = "· $priceText",
                    style = TratyTheme.typography.labelSmall,
                    fontWeight = if (isSelected || isBest) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected || isBest) textColor else TratyTheme.colors.onSurfaceVariant,
                )
            }
            if (isBest) {
                Text(
                    text = "выгодно",
                    style = TratyTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }
        }
    }
}
