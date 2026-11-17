package ru.my.traty.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ru.my.traty.data.period.SpendingPace
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.LocalDate

/**
 * «В среднем в день» и «Прогноз к концу периода». Прогноз линейный: сколько потрачено, плюс средняя в день
 * на каждый оставшийся день. Сравнивается с итогом прошлого периода, если он есть.
 */
@Composable
internal fun SpendingPaceCard(pace: SpendingPace, endDate: LocalDate, prevSum: Long, span: StatsSpan) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Row(Modifier.padding(TratyTheme.spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text("В среднем в день", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                TratyAmountText(amountKopecks = pace.dailyAverage, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
                Text(
                    text = "за ${days(pace.daysPassed)}",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            val forecast = pace.forecast
            if (forecast != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Прогноз к ${endDate.dayOfMonth} ${shortMonth(endDate)}",
                        style = TratyTheme.typography.labelMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    TratyAmountText(amountKopecks = forecast, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
                    val note = if (prevSum > 0) {
                        val diff = forecast - prevSum
                        val label = when (span) {
                            StatsSpan.PERIOD -> "прошлого периода"
                            StatsSpan.DAY -> "предыдущего дня"
                            StatsSpan.WEEK -> "прошлой недели"
                            StatsSpan.DATES -> "прошлых дат"
                        }
                        when {
                            diff > 0 -> "на ${formatRub(diff)} больше $label"
                            diff < 0 -> "на ${formatRub(-diff)} меньше $label"
                            else -> "как в прошлый раз"
                        }
                    } else "осталось ${days(pace.daysLeft)}"
                    Text(
                        text = note,
                        style = TratyTheme.typography.bodySmall,
                        color = if (prevSum > 0 && forecast > prevSum) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val SHORT_MONTHS_GEN = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun shortMonth(d: LocalDate) = SHORT_MONTHS_GEN[d.monthValue - 1]

