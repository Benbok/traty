package ru.my.traty.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.data.RingSlice
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import kotlin.math.roundToInt

/** Кольцевая диаграмма по категориям: крупные категории своими долями, мелкие — «Остальное». */
@Composable
internal fun CategoryRingCard(slices: List<RingSlice>, sum: Long) {
    if (slices.isEmpty()) return
    val pal = ringPalette()
    val colors = slices.mapIndexed { i, sl -> if (sl.categoryId == null) RING_REST else pal[i % pal.size] }
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Row(Modifier.padding(TratyTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 18.dp.toPx()
                    val gap = if (slices.size > 1) 1.5f else 0f
                    var startAngle = -90f
                    slices.forEachIndexed { i, sl ->
                        val sweep = 360f * sl.fraction
                        drawArc(
                            color = colors[i],
                            startAngle = startAngle + gap / 2,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke),
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Всего", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                    Text(formatRub(sum), style = TratyTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(TratyTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                slices.forEachIndexed { i, sl ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(colors[i])
                        if (sl.categoryId != null && sl.emoji.isNotBlank()) {
                            TratyCategoryBadge(emoji = sl.emoji, categoryName = sl.name, size = 18.dp)
                            Spacer(Modifier.width(TratyTheme.spacing.xs))
                        }
                        Text(
                            text = sl.name,
                            style = TratyTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = TratyTheme.spacing.xs),
                        )
                        Text(
                            text = "${(sl.fraction * 100).roundToInt()}%",
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
