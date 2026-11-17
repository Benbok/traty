package ru.my.traty.ui.apartments

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.my.traty.data.apartments.*
import java.util.Locale

internal fun intervalText(value: ConsumptionInterval, unit: String): String =
    "${ReadingDates.decode(value.from)} — ${ReadingDates.decode(value.to)}: ${ReadingValues.format(value.milli)} $unit за ${value.days} дн.; ${String.format(Locale.forLanguageTag("ru"), "%.3f", value.daily)} $unit/сутки"

@Composable
internal fun ConsumptionCard(comparison: ConsumptionComparison?, unit: String) {
    HousingCard("Два последних завершённых интервала") {
        if (comparison == null) Text("Недостаточно данных для сравнения: нужны три замера с общими датами и известное начало учёта.")
        else {
            Text(intervalText(comparison.previous, unit))
            Text(intervalText(comparison.current, unit))
            Text(comparison.dailyChangePercent?.let { "Среднесуточный расход изменился на ${String.format(Locale.forLanguageTag("ru"), "%+.1f", it)} %" } ?: "В предыдущем интервале расход нулевой; процент не рассчитывается.")
        }
    }
}
