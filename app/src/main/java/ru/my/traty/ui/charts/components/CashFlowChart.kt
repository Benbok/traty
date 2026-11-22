package ru.my.traty.ui.charts.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.CashFlowGraph
import ru.my.traty.ui.charts.model.CashFlowGranularity
import ru.my.traty.ui.charts.model.CashFlowKind
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.stats.ModeChip
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatShortDate
import ru.my.traty.ui.formatRub
import java.time.LocalDate

@Composable
fun CashFlowChart(
    graph: CashFlowGraph,
    kind: CashFlowKind,
    allCategories: Boolean,
    onKindChange: (CashFlowKind) -> Unit,
    modifier: Modifier = Modifier,
    categoryPicker: @Composable () -> Unit = {},
) {
    var drilledInterval by remember(kind, graph.intervals) {
        mutableStateOf<ClosedRange<LocalDate>?>(null)
    }
    val plotted = remember(graph, drilledInterval) {
        drilledInterval?.let(graph::daysWithin) ?: graph
    }
    var selectedBucket by remember(kind, graph.intervals) {
        mutableStateOf<ClosedRange<LocalDate>?>(null)
    }
    var selectedDay by remember(kind, drilledInterval) { mutableStateOf<ClosedRange<LocalDate>?>(null) }
    val selectedInterval = if (drilledInterval == null) selectedBucket else selectedDay
    var showOperations by remember(kind, plotted.intervals) { mutableStateOf(false) }
    val active = selectedInterval?.let(plotted.intervals::indexOf)?.takeIf { it >= 0 }
        ?: plotted.intervals.lastIndex
    val colors = listOf(
        if (kind == CashFlowKind.EXPENSE) TratyTheme.colors.expense else TratyTheme.colors.income,
        TratyTheme.colors.info,
    )

    BackHandler(enabled = drilledInterval != null && !showOperations) { drilledInterval = null }

    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
        shape = TratyTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Text(
                if (kind == CashFlowKind.EXPENSE) "Динамика трат" else "Динамика доходов",
                style = TratyTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                listOf(CashFlowKind.EXPENSE, CashFlowKind.INCOME).forEach { option ->
                    ModeChip(title = option.title, selected = option == kind) { onKindChange(option) }
                }
            }
            Column { categoryPicker() }
            CashFlowPeriodTotals(graph, kind, allCategories, colors)
            if (active < 0) {
                Text(
                    "Нет операций за этот период",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            } else {
                if (drilledInterval != null) {
                    TratyTextButton(onClick = { drilledInterval = null }) {
                        Text(if (graph.granularity == CashFlowGranularity.WEEK) "К неделям" else "К месяцам")
                    }
                }
                CashFlowPlot(plotted, colors, active) {
                    if (drilledInterval == null) selectedBucket = plotted.intervals[it]
                    else selectedDay = plotted.intervals[it]
                }

                CashFlowIntervalDetails(
                    graph = plotted,
                    active = active,
                    colors = colors,
                    showOperations = showOperations,
                    onShowOperations = { showOperations = it },
                    onDrill = { drilledInterval = it },
                )
            }
        }
    }
}

@Composable
private fun CashFlowPeriodTotals(
    graph: CashFlowGraph,
    kind: CashFlowKind,
    allCategories: Boolean,
    colors: List<Color>,
) {
    val totals = remember(graph.series) { graph.series.map { it.amounts.sum() } }
    val isExpense = kind == CashFlowKind.EXPENSE
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
        color = TratyTheme.colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Text(
                if (isExpense) "Расходы за выбранный период" else "Доходы за выбранный период",
                style = TratyTheme.typography.labelMedium,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            if (allCategories) {
                val label = if (isExpense) "Все категории" else "Все источники"
                CashFlowAmount(label, totals.singleOrNull() ?: 0L, colors.first())
            } else {
                graph.series.forEachIndexed { index, series ->
                    CashFlowAmount(series.label, totals[index], colors[index])
                }
            }
        }
    }
}

@Composable
private fun CashFlowIntervalDetails(
    graph: CashFlowGraph,
    active: Int,
    colors: List<Color>,
    showOperations: Boolean,
    onShowOperations: (Boolean) -> Unit,
    onDrill: (ClosedRange<LocalDate>) -> Unit,
) {
    val interval = graph.intervals[active]
    Text(intervalLabel(interval), style = TratyTheme.typography.titleSmall)
    graph.series.forEachIndexed { index, series ->
        CashFlowAmount(series.label, series.amounts[active], colors[index])
    }
    if (graph.granularity != CashFlowGranularity.DAY) {
        TratyTextButton(onClick = { onDrill(interval) }) { Text("Разбивка по дням") }
    } else {
        val operations = graph.operationsByDay[interval.start].orEmpty()
        if (operations.isNotEmpty()) {
            TratyTextButton(onClick = { onShowOperations(true) }) { Text("Операции за день") }
            if (showOperations) {
                CashFlowDayOperations(interval.start, operations, colors) { onShowOperations(false) }
            }
        }
    }
}

@Composable
internal fun CashFlowAmount(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.weight(1f), style = TratyTheme.typography.bodyMedium)
        Text(formatRub(amount), style = TratyTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CashFlowPlot(graph: CashFlowGraph, colors: List<Color>, active: Int, onSelect: (Int) -> Unit) {
    val count = graph.intervals.size
    val maxAmount = graph.series.maxOf { it.amounts.max() }
    val grid = TratyTheme.colors.borderSubtle
    val selection = TratyTheme.colors.primary.copy(alpha = 0.12f)
    val description = buildString {
        append(intervalLabel(graph.intervals[active]))
        graph.series.forEach { append(", ${it.label} ${formatRub(it.amounts[active])}") }
    }
    if (maxAmount > 0L) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Шкала до ${formatRub(maxAmount)}",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
    Canvas(
        Modifier.fillMaxWidth().height(156.dp)
            .semantics {
                contentDescription = description
                customActions = buildList {
                    if (active > 0) add(CustomAccessibilityAction("Предыдущий интервал") {
                        onSelect(active - 1)
                        true
                    })
                    if (active < count - 1) add(CustomAccessibilityAction("Следующий интервал") {
                        onSelect(active + 1)
                        true
                    })
                }
            }
            .pointerInput(graph.intervals) {
                detectTapGestures { onSelect(bucketAt(it.x, size.width, count)) }
            }
            .pointerInput(graph.intervals) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(bucketAt(it.x, size.width, count)) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSelect(bucketAt(change.position.x, size.width, count))
                    },
                )
            },
    ) {
        val slot = size.width / count
        val selectedWidth = maxOf(slot, 3.dp.toPx()).coerceAtMost(size.width)
        val selectedLeft = ((active + 0.5f) * slot - selectedWidth / 2)
            .coerceIn(0f, size.width - selectedWidth)
        drawRoundRect(selection, Offset(selectedLeft, 0f), Size(selectedWidth, size.height), CornerRadius(3.dp.toPx()))
        for (step in 0..4) {
            val y = size.height * step / 4
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val groupWidth = (slot * 0.8f).coerceAtMost(36.dp.toPx())
        val barSlot = groupWidth / graph.series.size
        val barWidth = if (graph.series.size > 1) barSlot * 0.84f else barSlot
        graph.series.forEachIndexed { seriesIndex, series ->
            series.amounts.forEachIndexed { index, amount ->
                if (amount > 0) {
                    val height = (size.height * (amount.toDouble() / maxAmount)).toFloat().coerceAtLeast(1.dp.toPx())
                    val left = (index + 0.5f) * slot - groupWidth / 2 + seriesIndex * barSlot + (barSlot - barWidth) / 2
                    drawRoundRect(
                        color = colors[seriesIndex],
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(minOf(2.dp.toPx(), barWidth / 2, height / 2)),
                    )
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (count == 1) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        listOf(0, (count - 1) / 2, count - 1).distinct().forEach { index ->
            Text(formatShortDate(graph.intervals[index].start), style = TratyTheme.typography.labelSmall)
        }
    }
}

private fun bucketAt(x: Float, width: Int, count: Int): Int =
    if (width <= 0) 0 else (x / width * count).toInt().coerceIn(0, count - 1)

private fun intervalLabel(interval: ClosedRange<LocalDate>): String =
    if (interval.start == interval.endInclusive) formatDate(interval.start)
    else formatDateSpan(interval.start, interval.endInclusive)
