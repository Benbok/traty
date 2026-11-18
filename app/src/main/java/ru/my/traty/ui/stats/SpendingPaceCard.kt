package ru.my.traty.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import ru.my.traty.data.period.SpendingPace
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRubSigned
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatShortDate
import java.time.LocalDate

/** Основные суммы отдельно от сравнения: длинная подпись не сжимает колонку прогноза. */
@Composable
internal fun SpendingPaceCard(pace: SpendingPace, endDate: LocalDate, prevSum: Long, span: StatsSpan) {
    TratyCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            PaceMetrics(pace, endDate)
            pace.forecast?.let { forecast ->
                HorizontalDivider(color = TratyTheme.colors.border)
                if (prevSum > 0) ForecastComparison(forecast - prevSum, span)
                else Text(
                    "Осталось " + days(pace.daysLeft),
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaceMetrics(pace: SpendingPace, endDate: LocalDate) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val amountStyle = TratyTheme.typography.mediumAmount.copy(fontWeight = FontWeight.Bold)
    val amountWidth = with(density) {
        maxOf(
            measurer.measure(formatRub(pace.dailyAverage), amountStyle, maxLines = 1).size.width,
            pace.forecast?.let { measurer.measure(formatRub(it), amountStyle, maxLines = 1).size.width } ?: 0,
        ).toDp()
    }
    val gap = TratyTheme.spacing.lg
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // На небольшом экране и при крупном шрифте не ужимаем суммы в половину карточки.
        val stacked = amountWidth * 2 + gap > maxWidth || density.fontScale > 1.2f
        val forecast = pace.forecast
        if (stacked || forecast == null) {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.lg)) {
                PaceMetric("В среднем в день", pace.dailyAverage, "за " + days(pace.daysPassed))
                if (forecast != null) PaceMetric("Прогноз к " + formatShortDate(endDate), forecast, "при текущем темпе")
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.lg)) {
                PaceMetric(
                    "В среднем в день", pace.dailyAverage, "за " + days(pace.daysPassed),
                    Modifier.weight(1f),
                )
                PaceMetric(
                    "Прогноз к " + formatShortDate(endDate), forecast, "при текущем темпе",
                    Modifier.weight(1f), endAligned = true,
                )
            }
        }
    }
}

@Composable
private fun PaceMetric(
    title: String,
    amount: Long,
    caption: String,
    modifier: Modifier = Modifier,
    endAligned: Boolean = false,
) {
    Column(
        modifier,
        horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        Text(title, style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
        TratyAmountText(amount, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
        Text(caption, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastComparison(diff: Long, span: StatsSpan) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        ForecastDifferenceBadge(diff)
        Text(
            when (span) {
                StatsSpan.PERIOD -> "к прошлому периоду"
                StatsSpan.DAY -> "к предыдущему дню"
                StatsSpan.WEEK -> "к прошлой неделе"
                StatsSpan.DATES -> "к предыдущему отрезку"
            },
            modifier = Modifier.align(Alignment.CenterVertically),
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ForecastDifferenceBadge(diff: Long) {
    val background = when {
        diff > 0 -> TratyTheme.colors.expenseContainer
        diff < 0 -> TratyTheme.colors.incomeContainer
        else -> TratyTheme.colors.surfaceContainerHigh
    }
    val foreground = when {
        diff > 0 -> TratyTheme.colors.expense
        diff < 0 -> TratyTheme.colors.income
        else -> TratyTheme.colors.onSurfaceVariant
    }
    Surface(shape = TratyTheme.shapes.small, color = background, contentColor = foreground) {
        Row(
            Modifier.padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    diff > 0 -> Icons.Filled.ArrowUpward
                    diff < 0 -> Icons.Filled.ArrowDownward
                    else -> Icons.Filled.Remove
                },
                contentDescription = null,
                modifier = Modifier.size(TratyTheme.spacing.lg),
            )
            Text(
                if (diff == 0L) "Без изменений" else formatRubSigned(diff),
                style = TratyTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val previewPace = SpendingPace(929_997L, 3, 7, 309_999L, 2_169_993L)

@Preview(name = "Рост расходов", widthDp = 400, showBackground = true)
@Composable
private fun ForecastIncreasePreview() {
    TratyTheme(darkTheme = false) {
        SpendingPaceCard(previewPace, LocalDate.of(2026, 11, 22), 1_874_800L, StatsSpan.WEEK)
    }
}

@Preview(name = "Снижение · тёмная тема", widthDp = 400, showBackground = true)
@Composable
private fun ForecastDecreasePreview() {
    TratyTheme(darkTheme = true) {
        SpendingPaceCard(previewPace, LocalDate.of(2026, 11, 22), 2_500_000L, StatsSpan.WEEK)
    }
}

@Preview(name = "Крупный шрифт · без изменений", widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun ForecastLargeTextPreview() {
    TratyTheme(darkTheme = false) {
        SpendingPaceCard(previewPace, LocalDate.of(2026, 11, 22), 2_169_993L, StatsSpan.WEEK)
    }
}
