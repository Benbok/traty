package ru.my.traty.ui.apartments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import ru.my.traty.data.apartments.ReadingValues
import ru.my.traty.data.apartments.ConsumptionComparison
import ru.my.traty.data.apartments.ConsumptionInterval
import ru.my.traty.data.apartments.ConsumptionTrend
import ru.my.traty.data.apartments.ReadingDates
import ru.my.traty.data.apartments.RegisterSeries
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.DatePickerModal
import ru.my.traty.ui.stats.ModeChip
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeterConsumptionChart(series: List<RegisterSeries>, unit: String) {
    val today = LocalDate.now()
    val defaultStart = today.minusMonths(6)
    var startMillis by rememberSaveable { mutableStateOf(ReadingDates.encode(defaultStart)) }
    var endMillis by rememberSaveable { mutableStateOf(ReadingDates.encode(today)) }
    var choosingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var registerId by rememberSaveable { mutableStateOf<Long?>(null) }
    val register = series.firstOrNull { it.registerId == registerId } ?: series.firstOrNull()
    val intervals = remember(register, startMillis, endMillis) {
        register?.let { ConsumptionTrend.forPeriod(it.points, startMillis, endMillis) }.orEmpty()
    }

    TratyCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text("Динамика потребления", style = TratyTheme.typography.titleMedium)
            HousingHint("Средний расход за сутки между замерами")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                TratyTextButton(onClick = { choosingStart = true }) {
                    Text("С ${HousingDates.format(startMillis)}")
                }
                TratyTextButton(onClick = { choosingStart = false }) {
                    Text("По ${HousingDates.format(endMillis)}")
                }
            }
            if (startMillis != ReadingDates.encode(defaultStart) || endMillis != ReadingDates.encode(today)) {
                TratyTextButton(onClick = {
                    startMillis = ReadingDates.encode(defaultStart)
                    endMillis = ReadingDates.encode(today)
                }) { Text("Последние 6 месяцев") }
            }
            if (series.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    series.forEach { item ->
                        ModeChip(registerTitle(item.code), selected = item == register) {
                            registerId = item.registerId
                        }
                    }
                }
            }
            when {
                startMillis > endMillis -> HousingHint("Дата начала должна быть не позже даты окончания.")
                intervals.isEmpty() -> HousingHint("За этот период нет интервалов расхода. Измените даты или добавьте показания.")
                else -> ConsumptionTrendPlot(intervals, startMillis, endMillis, unit)
            }
        }
    }
    choosingStart?.let { start ->
        DatePickerModal(
            initialDate = ReadingDates.decode(if (start) startMillis else endMillis),
            latestDate = today,
            onDateSelected = {
                if (start) startMillis = ReadingDates.encode(it) else endMillis = ReadingDates.encode(it)
            },
            onDismiss = { choosingStart = null },
        )
    }
}

@Composable
internal fun ConsumptionTrendPlot(intervals: List<ConsumptionInterval>, from: Long, to: Long, unit: String) {
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = intervals.firstOrNull { it.to == selectedDate } ?: intervals.last()
    val index = intervals.indexOf(selected)
    ConsumptionTrendCanvas(intervals, from, to, selected) { selectedDate = it.to }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(HousingDates.format(from), style = TratyTheme.typography.labelSmall)
        if (from != to) Text(HousingDates.format(to), style = TratyTheme.typography.labelSmall)
    }

    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
    Text(
        "${dailyText(selected.daily)} $unit/сутки",
        style = TratyTheme.typography.titleMedium,
        color = TratyTheme.colors.primary,
    )
    HousingHint("${HousingDates.format(selected.from)} — ${HousingDates.format(selected.to)}")
    HousingHint("Всего ${ReadingValues.format(selected.milli)} $unit за ${selected.days} дн.")
    val previous = intervals.getOrNull(index - 1)
    if (previous != null && previous.to == selected.from) {
        ConsumptionComparison(previous, selected).dailyChangePercent?.let { percent ->
            val difference = String.format(Locale.forLanguageTag("ru"), "%+.1f", percent)
            HousingHint("$difference % к предыдущему интервалу")
        }
    }
}

@Composable
private fun ConsumptionTrendCanvas(
    intervals: List<ConsumptionInterval>,
    from: Long,
    to: Long,
    selected: ConsumptionInterval,
    onSelect: (ConsumptionInterval) -> Unit,
) {
    val select by rememberUpdatedState(onSelect)
    val scale = maxOf(intervals.maxOf { it.daily }, 0.003)
    val index = intervals.indexOf(selected)
    val accent = TratyTheme.colors.primary
    val grid = TratyTheme.colors.borderSubtle
    val labelColor = TratyTheme.colors.onSurfaceVariant
    val inset = TratyTheme.spacing.sm
    val measurer = rememberTextMeasurer()
    val labelStyle = TratyTheme.typography.labelSmall
    val labels = (0..3).map { step -> measurer.measure(dailyText(scale * (3 - step) / 3), labelStyle) }
    val labelWidth = labels.maxOf { it.size.width }
    val labelHeight = labels.maxOf { it.size.height }
    fun fraction(date: Long): Float =
        if (from == to) 0.5f else ((date - from).toDouble() / (to - from)).toFloat()

    Canvas(
        Modifier.fillMaxWidth().height(TratyTheme.spacing.xxxl * 4)
            .semantics {
                contentDescription = "График среднего расхода за сутки"
                customActions = buildList {
                    intervals.getOrNull(index - 1)?.let { previous ->
                        add(CustomAccessibilityAction("Предыдущий интервал") { onSelect(previous); true })
                    }
                    intervals.getOrNull(index + 1)?.let { next ->
                        add(CustomAccessibilityAction("Следующий интервал") { onSelect(next); true })
                    }
                }
            }
            .pointerInput(intervals, from, to, inset, labelWidth) {
                detectTapGestures { tap ->
                    val padding = inset.toPx()
                    val start = labelWidth + padding
                    val width = (size.width - start - padding).coerceAtLeast(0f)
                    select(intervals.minBy { abs(start + width * fraction(it.to) - tap.x) })
                }
            },
    ) {
        val padding = inset.toPx()
        val start = labelWidth + padding
        val width = (size.width - start - padding).coerceAtLeast(0f)
        val top = maxOf(padding, labelHeight / 2f)
        val height = (size.height - 2 * top).coerceAtLeast(0f)
        fun position(interval: ConsumptionInterval) = Offset(
            start + width * fraction(interval.to),
            top + height * (1 - interval.daily / scale).toFloat(),
        )
        labels.forEachIndexed { step, label ->
            val y = top + height * step / labels.lastIndex
            drawText(label, color = labelColor, topLeft = Offset((labelWidth - label.size.width).toFloat(), y - label.size.height / 2f))
            drawLine(grid, Offset(start, y), Offset(start + width, y), padding / 8)
        }
        val marker = position(selected)
        drawLine(accent.copy(alpha = 0.35f), Offset(marker.x, top), Offset(marker.x, top + height), padding / 8)
        intervals.zipWithNext().forEach { (a, b) ->
            if (a.to == b.from) drawLine(accent, position(a), position(b), padding / 3)
        }
        intervals.forEach { interval ->
            drawCircle(accent, padding / if (interval == selected) 1.5f else 3f, position(interval))
        }
    }
}

private fun dailyText(value: Double): String =
    String.format(Locale.forLanguageTag("ru"), "%.3f", value).trimEnd('0').trimEnd(',')
