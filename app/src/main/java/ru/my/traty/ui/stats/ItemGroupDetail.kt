package ru.my.traty.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.items.ItemGroup
import ru.my.traty.data.items.ItemGroupSale
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.ui.RU
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyMaterialIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatShortDate
import ru.my.traty.ui.formatTime
import ru.my.traty.ui.home.dayTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val TIME_ONLY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", RU)

private data class SaleDayGroup(
    val date: LocalDate,
    val totalKopecks: Long,
    val sales: List<ItemGroupSale>,
)

@Composable
internal fun ItemGroupDetailContent(
    group: ItemGroup,
    periodLabel: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }

    var selectedStoreKey by remember(group) { mutableStateOf<String?>(null) }
    val storeOptions = remember(group.sales) { StoreFilterOptions.build(group.sales) }
    val bestStoreKeys = remember(storeOptions) { StoreFilterOptions.findBestStoreKeys(storeOptions) }

    val filteredSales = remember(group.sales, selectedStoreKey) {
        when (selectedStoreKey) {
            null -> group.sales
            NO_STORE_KEY -> group.sales.filter { it.place.isNullOrBlank() }
            else -> group.sales.filter { it.place == selectedStoreKey }
        }
    }
    val dayGroups = remember(filteredSales, zone) { groupByDayNewestFirst(filteredSales, zone) }

    Column(Modifier.fillMaxWidth()) {
        DetailHeader(group = group, periodLabel = periodLabel, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(
                start = TratyTheme.spacing.lg,
                end = TratyTheme.spacing.lg,
                top = TratyTheme.spacing.sm,
                bottom = TratyTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            item(key = "price-chart") {
                PriceHistoryCard(group = group)
            }
            item(key = "history-header") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                    modifier = Modifier.padding(top = TratyTheme.spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "История покупок",
                            style = TratyTheme.typography.titleSmall,
                            color = TratyTheme.colors.onSurface,
                        )
                        if (selectedStoreKey != null) {
                            Text(
                                text = "${filteredSales.size} из ${group.sales.size}",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (storeOptions.isNotEmpty() && group.places.isNotEmpty()) {
                        StoreFilterChipsRow(
                            stores = storeOptions,
                            totalSalesCount = group.sales.size,
                            selectedKey = selectedStoreKey,
                            onSelectKey = { selectedStoreKey = it },
                            bestStoreKeys = bestStoreKeys,
                            priceUnit = group.priceUnit,
                            modifier = Modifier.padding(top = TratyTheme.spacing.xxs),
                        )
                    }
                }
            }
            items(dayGroups, key = { it.date.toEpochDay() }) { dayGroup ->
                DaySalesSection(
                    dayGroup = dayGroup,
                    today = today,
                    zone = zone,
                    groupName = group.displayName,
                    priceUnit = group.priceUnit,
                )
            }
        }
    }
}

private fun groupByDayNewestFirst(sales: List<ItemGroupSale>, zone: ZoneId): List<SaleDayGroup> =
    sales.asReversed()
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .entries
        .map { (date, daySales) ->
            SaleDayGroup(
                date = date,
                totalKopecks = daySales.mapNotNull { it.totalPriceKopecks }.sum(),
                sales = daySales,
            )
        }

@Composable
private fun DetailHeader(
    group: ItemGroup,
    periodLabel: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TratyTheme.spacing.xs, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyMaterialIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку товаров")
        }
        Spacer(Modifier.width(TratyTheme.spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.displayName,
                style = TratyTheme.typography.titleMedium,
                color = TratyTheme.colors.onSurface,
            )
            val totalPart = if (group.sales.any { it.totalPriceKopecks != null }) {
                "Всего ${formatRub(group.totalSpentKopecks)}"
            } else null
            Text(
                text = listOfNotNull(periodLabel, timesWord(group.count), totalPart).joinToString(" · "),
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PriceHistoryCard(group: ItemGroup) {
    val priced = remember(group) { group.sales.filter { it.unitPriceKopecks != null } }
    val unitSuffix = group.priceUnit?.let { "/$it" }.orEmpty()
    TratyCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Цена по датам", style = TratyTheme.typography.titleSmall)
                PriceRangeRow(group)
            }
            if (priced.size < 2) {
                Text(
                    text = "График появится, когда за период будет хотя бы 2 покупки с ценой.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            } else {
                PriceLineChart(priced = priced, unitSuffix = unitSuffix)
            }
        }
    }
}

@Composable
private fun PriceLineChart(priced: List<ItemGroupSale>, unitSuffix: String) {
    var selectedIndex by remember(priced) { mutableIntStateOf(priced.lastIndex) }
    val safeIndex = selectedIndex.coerceIn(priced.indices)
    val selected = priced[safeIndex]
    val minPrice = remember(priced) { priced.minOf { it.unitPriceKopecks ?: 0L } }
    val maxPrice = remember(priced) { priced.maxOf { it.unitPriceKopecks ?: 0L } }
    val zone = remember { ZoneId.systemDefault() }
    val lineColor = TratyTheme.colors.primary
    val gridColor = TratyTheme.colors.surfaceContainerHigh
    val haloColor = TratyTheme.colors.primary.copy(alpha = 0.25f)
    val dotCenterColor = TratyTheme.colors.surfaceContainer

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .pointerInput(priced) {
                detectTapGestures { tap ->
                    val pad = 12.dp.toPx()
                    val usableWidth = (size.width - (pad * 2)).coerceAtLeast(1f)
                    val nearest = priced.indices.minByOrNull { i ->
                        val px = pad + usableWidth * i / (priced.size - 1)
                        abs(px - tap.x)
                    } ?: return@detectTapGestures
                    selectedIndex = nearest
                }
            },
    ) {
        val padX = 12.dp.toPx()
        val padY = 12.dp.toPx()
        val w = (size.width - (padX * 2)).coerceAtLeast(1f)
        val h = (size.height - (padY * 2)).coerceAtLeast(1f)
        val span = (maxPrice - minPrice).toFloat()

        fun x(i: Int) = padX + w * i / (priced.size - 1)
        fun y(price: Long): Float = if (span <= 0f) {
            padY + h / 2f
        } else {
            padY + h - h * ((price - minPrice).toFloat() / span)
        }

        for (k in 0..2) {
            val gy = padY + h * k / 2f
            drawLine(gridColor, Offset(padX, gy), Offset(size.width - padX, gy), strokeWidth = 1.dp.toPx())
        }

        val path = Path().apply {
            priced.forEachIndexed { i, sale ->
                val pt = Offset(x(i), y(sale.unitPriceKopecks ?: 0L))
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        priced.forEachIndexed { i, sale ->
            val center = Offset(x(i), y(sale.unitPriceKopecks ?: 0L))
            if (i == safeIndex) {
                drawCircle(haloColor, radius = 9.dp.toPx(), center = center)
                drawCircle(lineColor, radius = 5.dp.toPx(), center = center)
                drawCircle(dotCenterColor, radius = 2.dp.toPx(), center = center)
            } else {
                drawCircle(lineColor, radius = 3.5.dp.toPx(), center = center)
            }
        }
    }

    val firstDate = remember(priced) { Instant.ofEpochMilli(priced.first().timestamp).atZone(zone).toLocalDate() }
    val lastDate = remember(priced) { Instant.ofEpochMilli(priced.last().timestamp).atZone(zone).toLocalDate() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatShortDate(firstDate), style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
        Text(formatShortDate(lastDate), style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
    }

    val selectedPrice = selected.unitPriceKopecks?.let { formatRub(it) + unitSuffix }.orEmpty()
    val summary = listOfNotNull(formatTime(selected.timestamp), selected.place, selectedPrice).joinToString(" · ")
    Text(
        text = summary,
        style = TratyTheme.typography.labelMedium,
        color = TratyTheme.colors.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DaySalesSection(
    dayGroup: SaleDayGroup,
    today: LocalDate,
    zone: ZoneId,
    groupName: String,
    priceUnit: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TratyTheme.spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dayTitle(dayGroup.date, today),
                style = TratyTheme.typography.labelLarge,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            if (dayGroup.totalKopecks > 0L) {
                Text(
                    text = formatRub(dayGroup.totalKopecks),
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surfaceContainer,
        ) {
            Column {
                dayGroup.sales.forEachIndexed { index, sale ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = TratyTheme.colors.borderSubtle,
                            modifier = Modifier.padding(horizontal = TratyTheme.spacing.md),
                        )
                    }
                    SaleHistoryRow(
                        sale = sale,
                        zone = zone,
                        groupName = groupName,
                        priceUnit = priceUnit,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleHistoryRow(
    sale: ItemGroupSale,
    zone: ZoneId,
    groupName: String,
    priceUnit: String?,
) {
    val pack = remember(sale) { ItemMeasure.packOf(sale.name, sale.packMilli, sale.packUnit) }
    val measure = remember(sale, pack) { ItemMeasure.describe(sale.quantityMilli, sale.unit, pack) }
    val timeText = remember(sale.timestamp, zone) {
        Instant.ofEpochMilli(sale.timestamp).atZone(zone).format(TIME_ONLY_FMT)
    }
    val unitSuffix = priceUnit?.let { "/$it" }.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TratyTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text(
                    text = timeText,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                sale.place?.let { StoreChip(it) }
            }
            if (sale.name != groupName) {
                Text(
                    text = sale.name,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurface,
                )
            }
            if (!measure.isNullOrBlank()) {
                Text(
                    text = measure,
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(TratyTheme.spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = sale.unitPriceKopecks?.let { formatRub(it) + unitSuffix } ?: "Без цены",
                style = TratyTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (sale.unitPriceKopecks != null) TratyTheme.colors.onSurface else TratyTheme.colors.onSurfaceVariant,
            )
            val total = sale.totalPriceKopecks
            if (total != null && total != sale.unitPriceKopecks) {
                Text(
                    text = "всего ${formatRub(total)}",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
