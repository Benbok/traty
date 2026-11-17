package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ReadingCheckPreview(
    checks: List<Pair<MeterRegister, ReadingCheck>>,
    history: List<MeterReading>,
    date: Long?,
    snapshot: ApartmentSnapshot,
    onEditExisting: ((MeterReading) -> Unit)?,
) {
    checks.forEach { (reg, check) ->
        val meter = snapshot.meters.firstOrNull { it.id == reg.meterId }
        val unit = ResourceType.entries.firstOrNull { it.name == meter?.resource }?.unit.orEmpty()
        Text(registerTitle(reg.code), style = TratyTheme.typography.labelLarge)
        check.previous?.let { previous ->
            Text("Предыдущее: " + ReadingValues.format(previous.valueMilli) + " " + unit +
                " от " + HousingDates.format(previous.measuredAt), style = TratyTheme.typography.bodySmall)
        }
        check.consumptionMilli?.let {
            Text("Расход: " + ReadingValues.format(it) + " " + unit, color = TratyTheme.colors.primary)
        }
        check.error?.let { Text(it, color = TratyTheme.colors.expense) }
        if (check.previous == null && check.error == null) Text("Первый замер задаст исходную точку для расчёта расхода")
        if (check.unusuallyHigh) Text(
            "Средний расход в сутки более чем втрое выше предыдущего интервала. Проверьте число и дату.",
            color = TratyTheme.colors.warning,
        )
        val duplicate = history.firstOrNull { it.registerId == reg.id && it.measuredAt == date }
        if (duplicate != null && onEditExisting != null) {
            TratyTextButton(onClick = { onEditExisting(duplicate) }) { Text("Открыть запись за эту дату") }
        }
    }
}

@Composable
internal fun HighReadingConfirmation(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text("Числа и дату проверил, высокий расход подтверждаю", style = TratyTheme.typography.bodySmall)
    }
}
