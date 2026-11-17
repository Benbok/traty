package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.util.Locale

internal fun intervalText(value: ConsumptionInterval, unit: String): String =
    "${HousingDates.format(value.from)} — ${HousingDates.format(value.to)}: ${ReadingValues.format(value.milli)} $unit за ${value.days} дн.; ${String.format(Locale.forLanguageTag("ru"), "%.3f", value.daily)} $unit/сутки"

@Composable
internal fun ConsumptionCard(comparison: ConsumptionComparison?, unit: String) {
    TratyTonalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            Text("Сравнение расхода", style = TratyTheme.typography.titleMedium)
            if (comparison == null) {
                HousingHint("Пока недостаточно данных")
                Text(
                    "Для сравнения нужны три замера на общие даты и известное начало учёта. Без них общий расход не рассчитывается.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            } else {
                ConsumptionIntervalRow(comparison.current, unit, "Последний интервал")
                HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                ConsumptionIntervalRow(comparison.previous, unit, "Предыдущий интервал")
                comparison.dailyChangePercent?.let { percent ->
                    Text(
                        "${String.format(Locale.forLanguageTag("ru"), "%+.1f", percent)} % к предыдущему интервалу",
                        style = TratyTheme.typography.titleSmall,
                        color = TratyTheme.colors.primary,
                    )
                    HousingHint("Сравнивается расход в сутки: длительность интервалов может отличаться.")
                } ?: HousingHint("В предыдущем интервале расход нулевой — процент не рассчитывается.")
            }
        }
    }
}
