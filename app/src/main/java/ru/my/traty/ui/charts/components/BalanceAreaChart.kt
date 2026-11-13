package ru.my.traty.ui.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.BalancePoint
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatRubSigned
import kotlin.math.abs

/**
 * Интерактивный график изменения накопительного баланса во времени (Area / Spline Chart).
 */
@Composable
fun BalanceAreaChart(
    points: List<BalancePoint>,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val lineColor = if (points.lastOrNull()?.cumulativeBalanceKopecks ?: 0L >= 0) {
        TratyTheme.colors.primary
    } else {
        TratyTheme.colors.expense
    }
    val gridColor = TratyTheme.colors.borderSubtle
    val zeroLineColor = TratyTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)

    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
        shape = TratyTheme.shapes.large,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            // Заголовок и текущее выбранное значение
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Динамика баланса",
                        style = TratyTheme.typography.titleMedium,
                        color = TratyTheme.colors.onSurface,
                    )
                    val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
                    if (activePoint != null) {
                        Text(
                            text = if (selectedIndex != null) {
                                "${formatDate(activePoint.date)} · ${if (activePoint.deltaKopecks >= 0) "+" else ""}${formatRub(activePoint.deltaKopecks)} за день"
                            } else {
                                "Итоговый баланс периода"
                            },
                            style = TratyTheme.typography.bodySmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }

                val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
                if (activePoint != null) {
                    val isPositive = activePoint.cumulativeBalanceKopecks >= 0
                    Text(
                        text = formatRubSigned(activePoint.cumulativeBalanceKopecks),
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) TratyTheme.colors.income else TratyTheme.colors.expense,
                    )
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.md))

            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Недостаточно данных для построения графика",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(points) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        selectedIndex = findNearestIndex(offset.x, size.width.toFloat(), points.size)
                                    },
                                )
                            }
                            .pointerInput(points) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        selectedIndex = findNearestIndex(offset.x, size.width.toFloat(), points.size)
                                    },
                                    onDragEnd = {
                                        // Оставляем выбор до нового нажатия
                                    },
                                    onDragCancel = {},
                                    onDrag = { change, _ ->
                                        selectedIndex = findNearestIndex(change.position.x, size.width.toFloat(), points.size)
                                    },
                                )
                            },
                    ) {
                        val width = size.width
                        val height = size.height
                        val paddingTop = 16.dp.toPx()
                        val paddingBottom = 24.dp.toPx()
                        val usableHeight = height - paddingTop - paddingBottom

                        val values = points.map { it.cumulativeBalanceKopecks }
                        val rawMin = values.minOrNull() ?: 0L
                        val rawMax = values.maxOrNull() ?: 0L

                        // Добавляем запас по вертикали, чтобы кривая не прилипала к краям
                        val paddingValue = if (rawMax == rawMin) 100_000L else ((rawMax - rawMin) * 0.15f).toLong().coerceAtLeast(50_000L)
                        val minY = rawMin - paddingValue
                        val maxY = rawMax + paddingValue
                        val rangeY = (maxY - minY).coerceAtLeast(1L).toFloat()

                        fun xForIndex(i: Int): Float = (i.toFloat() / (points.size - 1)) * width
                        fun yForValue(v: Long): Float = height - paddingBottom - ((v - minY).toFloat() / rangeY) * usableHeight

                        // Сетка: 3 горизонтальные опорные линии
                        val steps = 3
                        for (i in 0..steps) {
                            val y = paddingTop + (usableHeight / steps) * i
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        // Линия нуля, если диапазон пересекает 0
                        if (minY < 0 && maxY > 0) {
                            val zeroY = yForValue(0L)
                            drawLine(
                                color = zeroLineColor,
                                start = Offset(0f, zeroY),
                                end = Offset(width, zeroY),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                            )
                        }

                        // Построение плавной кривой (Bézier spline)
                        val strokePath = Path()
                        val fillPath = Path()

                        val firstX = xForIndex(0)
                        val firstY = yForValue(points[0].cumulativeBalanceKopecks)

                        strokePath.moveTo(firstX, firstY)
                        fillPath.moveTo(firstX, height - paddingBottom)
                        fillPath.lineTo(firstX, firstY)

                        for (i in 1 until points.size) {
                            val prevX = xForIndex(i - 1)
                            val prevY = yForValue(points[i - 1].cumulativeBalanceKopecks)
                            val curX = xForIndex(i)
                            val curY = yForValue(points[i].cumulativeBalanceKopecks)

                            val midX = (prevX + curX) / 2f
                            strokePath.cubicTo(midX, prevY, midX, curY, curX, curY)
                            fillPath.cubicTo(midX, prevY, midX, curY, curX, curY)
                        }

                        val lastX = xForIndex(points.size - 1)
                        fillPath.lineTo(lastX, height - paddingBottom)
                        fillPath.close()

                        // Градиентная заливка под графиком
                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            startY = paddingTop,
                            endY = height - paddingBottom,
                        )
                        drawPath(fillPath, brush = gradientBrush)

                        // Линия графика
                        drawPath(
                            strokePath,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                        )

                        // Отрисовка выбранной точки и вертикального маркера
                        val activeIdx = selectedIndex
                        if (activeIdx != null && activeIdx in points.indices) {
                            val selX = xForIndex(activeIdx)
                            val selY = yForValue(points[activeIdx].cumulativeBalanceKopecks)

                            // Вертикальная пунктирная линия
                            drawLine(
                                color = lineColor.copy(alpha = 0.6f),
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, height - paddingBottom),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                            )

                            // Внешний ореол точки
                            drawCircle(
                                color = lineColor.copy(alpha = 0.25f),
                                radius = 9.dp.toPx(),
                                center = Offset(selX, selY),
                            )
                            // Белая подложка точки
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = Offset(selX, selY),
                            )
                            // Сердцевина точки
                            drawCircle(
                                color = lineColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(selX, selY),
                            )
                        }
                    }
                }

                // Нижние метки по оси X
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    val labelCount = 4
                    val step = ((points.size - 1) / (labelCount - 1)).coerceAtLeast(1)
                    val displayedIndices = (0 until labelCount).map { (it * step).coerceAtMost(points.size - 1) }.distinct()

                    displayedIndices.forEachIndexed { i, idx ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Text(
                            text = points[idx].label,
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun findNearestIndex(touchX: Float, totalWidth: Float, pointCount: Int): Int {
    if (pointCount <= 1 || totalWidth <= 0f) return 0
    val rawFrac = (touchX / totalWidth).coerceIn(0f, 1f)
    return (rawFrac * (pointCount - 1)).toInt().coerceIn(0, pointCount - 1)
}
