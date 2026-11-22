package ru.my.traty.ui.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.CashFlowPoint
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatDateNumeric
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatRubSigned
import kotlin.math.roundToInt

@Composable
fun CashFlowChart(
    points: List<CashFlowPoint>,
    modifier: Modifier = Modifier,
    filteredExpenses: Boolean = false,
    categoryPicker: @Composable () -> Unit = {},
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val active = points.getOrNull(selectedIndex ?: points.lastIndex)
    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
        shape = TratyTheme.shapes.large,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Доходы и расходы", style = TratyTheme.typography.titleMedium)
            Text(
                "Накопительно с начала выбранного периода",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            categoryPicker()
            if (active == null) {
                Box(Modifier.fillMaxWidth().height(192.dp), contentAlignment = Alignment.Center) {
                    Text("Нет операций за этот период", style = TratyTheme.typography.bodyMedium)
                }
            } else {
                CashFlowPlot(points, selectedIndex) { selectedIndex = it }
                Spacer(Modifier.height(TratyTheme.spacing.md))
                CashFlowDetails(active, filteredExpenses)
            }
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            Text(
                if (filteredExpenses) "Разница между всеми доходами и расходами выбранной категории"
                else "Разница доходов и расходов за период, не остаток на счетах",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CashFlowDetails(point: CashFlowPoint, filteredExpenses: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Text("На ${formatDate(point.date)}", style = TratyTheme.typography.labelMedium)
        CashFlowAmount("━ Доходы", formatRub(point.incomeKopecks), TratyTheme.colors.income)
        CashFlowAmount(
            if (filteredExpenses) "┄ Расходы категории" else "┄ Расходы",
            formatRub(point.expenseKopecks),
            TratyTheme.colors.expense,
        )
        CashFlowAmount(
            "Разница", formatRubSigned(point.netKopecks),
            if (point.netKopecks >= 0) TratyTheme.colors.income else TratyTheme.colors.expense,
        )
    }
}

@Composable
private fun CashFlowAmount(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TratyTheme.typography.bodySmall, color = color)
        Text(value, style = TratyTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CashFlowPlot(points: List<CashFlowPoint>, selected: Int?, onSelect: (Int) -> Unit) {
    val incomeColor = TratyTheme.colors.income
    val expenseColor = TratyTheme.colors.expense
    val gridColor = TratyTheme.colors.borderSubtle
    val maxValue = points.maxOf { maxOf(it.incomeKopecks, it.expenseKopecks) }.coerceAtLeast(1L)
    val active = points[selected ?: points.lastIndex]
    Text(
        "Общая шкала: 0 — ${formatRub(maxValue)}",
        style = TratyTheme.typography.labelSmall,
        color = TratyTheme.colors.onSurfaceVariant,
    )
    Canvas(
        Modifier.fillMaxWidth().height(192.dp)
            .semantics {
                contentDescription = "На ${formatDate(active.date)}: доходы ${formatRub(active.incomeKopecks)}, " +
                    "расходы ${formatRub(active.expenseKopecks)}, разница ${formatRubSigned(active.netKopecks)}"
                customActions = listOf(
                    CustomAccessibilityAction("Предыдущая дата") {
                        onSelect(((selected ?: points.lastIndex) - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Следующая дата") {
                        onSelect(((selected ?: points.lastIndex) + 1).coerceAtMost(points.lastIndex)); true
                    },
                )
            }
            .pointerInput(points) {
                detectTapGestures { onSelect(nearestPoint(it.x, size.width.toFloat(), points.size)) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(nearestPoint(it.x, size.width.toFloat(), points.size)) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSelect(nearestPoint(change.position.x, size.width.toFloat(), points.size))
                    },
                )
            },
    ) {
        drawCashFlow(points, selected, maxValue, incomeColor, expenseColor, gridColor)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (points.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        val indices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
        indices.forEach { index ->
            Text(formatDateNumeric(points[index].date), style = TratyTheme.typography.labelSmall)
        }
    }
}

private fun nearestPoint(x: Float, width: Float, count: Int): Int =
    if (count <= 1 || width <= 0f) 0
    else ((x / width).coerceIn(0f, 1f) * (count - 1)).roundToInt()

/** Ступени не изображают поступления или покупки между датами операций. */
private fun DrawScope.drawCashFlow(
    points: List<CashFlowPoint>,
    selected: Int?,
    maxValue: Long,
    incomeColor: Color,
    expenseColor: Color,
    gridColor: Color,
) {
    val inset = 8.dp.toPx()
    val plotHeight = size.height - inset * 2
    fun x(index: Int) = if (points.size == 1) size.width / 2 else size.width * index / points.lastIndex
    fun y(value: Long) = size.height - inset - (value.toDouble() / maxValue * plotHeight).toFloat()
    for (step in 0..3) {
        val top = inset + plotHeight * step / 3
        drawLine(gridColor, Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
    }
    val dashed = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
    fun series(color: Color, expense: Boolean) {
        fun value(index: Int) = if (expense) points[index].expenseKopecks else points[index].incomeKopecks
        val path = Path().apply {
            moveTo(x(0), y(value(0)))
            for (index in 1..points.lastIndex) {
                lineTo(x(index), y(value(index - 1)))
                lineTo(x(index), y(value(index)))
            }
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), pathEffect = if (expense) dashed else null))
        if (points.size == 1) drawCircle(color, 4.dp.toPx(), Offset(x(0), y(value(0))))
        selected?.takeIf { it in points.indices }?.let {
            drawCircle(color, 5.dp.toPx(), Offset(x(it), y(value(it))))
        }
    }
    selected?.takeIf { it in points.indices }?.let {
        drawLine(gridColor, Offset(x(it), inset), Offset(x(it), size.height - inset), 2.dp.toPx())
    }
    series(incomeColor, false)
    series(expenseColor, true)
}
