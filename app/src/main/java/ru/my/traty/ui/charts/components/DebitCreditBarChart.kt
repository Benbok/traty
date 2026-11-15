package ru.my.traty.ui.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.DebitCreditBar
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatRubSigned

/**
 * Сдвоенный столбчатый график сравнения дебета (доходов) и кредита (трат).
 */
@Composable
fun DebitCreditBarChart(
    bars: List<DebitCreditBar>,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember(bars) { mutableStateOf<Int?>(null) }

    val debitColor = TratyTheme.colors.income
    val creditColor = TratyTheme.colors.expense
    val gridColor = TratyTheme.colors.borderSubtle

    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
        shape = TratyTheme.shapes.large,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            // Заголовок и легенда
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Доходы и траты",
                        style = TratyTheme.typography.titleMedium,
                        color = TratyTheme.colors.onSurface,
                    )
                    Text(
                        text = "Поступления и списания",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }

                // Легенда
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(color = debitColor, label = "Доходы")
                    Spacer(Modifier.width(TratyTheme.spacing.md))
                    LegendItem(color = creditColor, label = "Траты")
                }
            }

            // Выбранный элемент (тултип)
            val active = selectedIndex?.let { bars.getOrNull(it) }
            if (active != null) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                TratyCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surfaceContainer,
                    shape = TratyTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = active.fullDateLabel,
                            style = TratyTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
                            Text(
                                text = "Доходы: +${formatRub(active.debitKopecks)}",
                                style = TratyTheme.typography.labelSmall,
                                color = debitColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Траты: ${formatRub(active.creditKopecks)}",
                                style = TratyTheme.typography.labelSmall,
                                color = creditColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.md))

            if (bars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Нет операций за этот период",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bars) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val idx = (offset.x / (size.width / bars.size.toFloat()))
                                            .toInt()
                                            .coerceIn(0, bars.size - 1)
                                        selectedIndex = if (selectedIndex == idx) null else idx
                                    },
                                )
                            },
                    ) {
                        val width = size.width
                        val height = size.height
                        val paddingTop = 12.dp.toPx()
                        val paddingBottom = 24.dp.toPx()
                        val usableHeight = height - paddingTop - paddingBottom

                        val maxVal = bars.maxOfOrNull { maxOf(it.debitKopecks, it.creditKopecks) } ?: 0L
                        val safeMax = if (maxVal <= 0L) 100_000L else (maxVal * 1.15f).toLong()

                        // Горизонтальные линии сетки
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

                        val slotWidth = width / bars.size.toFloat()
                        val barWidth = (slotWidth * 0.32f).coerceAtMost(16.dp.toPx()).coerceAtLeast(2.dp.toPx())
                        val barSpacing = 2.dp.toPx()
                        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                        bars.forEachIndexed { i, bar ->
                            val slotCenterX = i * slotWidth + slotWidth / 2f
                            val isSelected = selectedIndex == i

                            // Подсветка фона выбранного интервала
                            if (isSelected) {
                                drawRect(
                                    color = debitColor.copy(alpha = 0.08f),
                                    topLeft = Offset(i * slotWidth, paddingTop),
                                    size = Size(slotWidth, usableHeight),
                                )
                            }

                            // Столбик Дебета (Доходы)
                            val debitHeight = (bar.debitKopecks.toFloat() / safeMax) * usableHeight
                            val debitTop = height - paddingBottom - debitHeight
                            val debitLeft = slotCenterX - barWidth - barSpacing / 2f

                            if (debitHeight > 0) {
                                drawRoundRect(
                                    color = if (isSelected || selectedIndex == null) debitColor else debitColor.copy(alpha = 0.4f),
                                    topLeft = Offset(debitLeft, debitTop),
                                    size = Size(barWidth, debitHeight),
                                    cornerRadius = cornerRadius,
                                )
                            }

                            // Столбик Кредита (Траты)
                            val creditHeight = (bar.creditKopecks.toFloat() / safeMax) * usableHeight
                            val creditTop = height - paddingBottom - creditHeight
                            val creditLeft = slotCenterX + barSpacing / 2f

                            if (creditHeight > 0) {
                                drawRoundRect(
                                    color = if (isSelected || selectedIndex == null) creditColor else creditColor.copy(alpha = 0.4f),
                                    topLeft = Offset(creditLeft, creditTop),
                                    size = Size(barWidth, creditHeight),
                                    cornerRadius = cornerRadius,
                                )
                            }
                        }
                    }
                }

                // Подписи интервалов по оси X
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    val labelCount = minOf(6, bars.size)
                    val step = ((bars.size - 1) / (labelCount - 1)).coerceAtLeast(1)
                    val displayedIndices = (0 until labelCount).map { (it * step).coerceAtMost(bars.size - 1) }.distinct()

                    displayedIndices.forEachIndexed { i, idx ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Text(
                            text = bars[idx].label,
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(TratyTheme.spacing.xs))
        Text(
            text = label,
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}
