package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.DatePickerModal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

@Composable
internal fun SubmissionDialog(
    meterId: Long,
    readings: List<MeterReading>,
    registers: List<MeterRegister>,
    vm: MeterDetailViewModel,
    onDismiss: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(HousingDates.format(LocalDate.now())) }
    var month by rememberSaveable { mutableStateOf(HousingDates.formatMonth(YearMonth.now())) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingDay by rememberSaveable { mutableStateOf(false) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable {
        mutableStateOf(readings.groupBy { it.registerId }.values.mapNotNull { group ->
            group.maxByOrNull { it.measuredAt }?.id
        }.toLongArray())
    }
    val sorted = remember(readings) { readings.sortedByDescending { it.measuredAt } }
    val selectedIds = readings.mapNotNull { it.id.takeIf(selected::contains) }
    ReadingEditorSheet(
        title = "Отметить передачу",
        busy = vm.busy,
        canSave = selectedIds.isNotEmpty(),
        error = error ?: vm.message,
        onDismiss = onDismiss,
        onSave = {
            try {
                val period = HousingDates.parseMonth(month)
                val date = ReadingDates.encode(HousingDates.parse(day))
                vm.submitted(meterId, selectedIds, date, period.year * 100 + period.monthValue, onDismiss)
            } catch (_: DateTimeParseException) {
                error = "Проверьте дату передачи (дд.мм.гггг) и месяц (мм.гггг)."
            }
        },
    ) {
        if (readings.isEmpty()) {
            HousingHint("Все показания уже отмечены как переданные.")
            return@ReadingEditorSheet
        }
        HousingHint("Сохраните отметку о показаниях, которые вы уже передали поставщику.")
        TratyCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(TratyTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                HousingField(day, "Дата передачи", trailingIcon = {
                    TratyIconButton(Icons.Filled.DateRange, "Выбрать дату передачи", { choosingDay = true }, enabled = !vm.busy)
                }) {
                    day = it
                    error = null
                }
                HousingField(month, "За какой месяц", trailingIcon = {
                    TratyIconButton(Icons.Filled.CalendarMonth, "Выбрать месяц передачи", { choosingMonth = true }, enabled = !vm.busy)
                }) {
                    month = it
                    error = null
                }
            }
        }
        HousingSectionTitle("Показания для отметки")
        TratyCard(Modifier.fillMaxWidth()) {
            Column {
                sorted.forEachIndexed { index, reading ->
                    if (index > 0) TratyRowDivider()
                    val code = registers.firstOrNull { it.id == reading.registerId }?.code
                    SubmissionReadingRow(reading, code, reading.id in selected, !vm.busy) { checked ->
                        selected = if (checked) selected + reading.id else selected.filterNot { it == reading.id }.toLongArray()
                    }
                }
            }
        }
    }
    if (choosingDay && readings.isNotEmpty()) {
        DatePickerModal(
            initialDate = runCatching { HousingDates.parse(day) }.getOrNull(),
            latestDate = LocalDate.now(),
            onDateSelected = {
                day = HousingDates.format(it)
                error = null
            },
            onDismiss = { choosingDay = false },
        )
    }
    if (choosingMonth && readings.isNotEmpty()) {
        SubmissionMonthPicker(
            initial = runCatching { HousingDates.parseMonth(month) }.getOrElse { YearMonth.now() },
            onPick = {
                month = HousingDates.formatMonth(it)
                error = null
                choosingMonth = false
            },
            onDismiss = { choosingMonth = false },
        )
    }
}

@Composable
private fun SubmissionReadingRow(
    reading: MeterReading,
    code: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
        ) {
            Text(ReadingValues.format(reading.valueMilli), style = TratyTheme.typography.titleMedium)
            val date = HousingDates.format(reading.measuredAt)
            HousingHint(if (code == null) date else "$date · ${registerTitle(code)}")
        }
    }
}
