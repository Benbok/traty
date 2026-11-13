package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Диалог выбора периода «с даты по дату» (Material 3 календарь с диапазоном).
 * Если выбрана одна дата — период из одного дня.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.toPickerMillis(),
        initialSelectedEndDateMillis = initialEnd.toPickerMillis(),
    )
    val start = state.selectedStartDateMillis?.toPickerDate()
    val end = state.selectedEndDateMillis?.toPickerDate() ?: start

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TratyTextButton(
                onClick = {
                    if (start != null && end != null) onRangeSelected(start, end)
                    onDismiss()
                },
                enabled = start != null,
                contentColor = TratyTheme.colors.primary,
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        shape = TratyTheme.shapes.large,
        colors = DatePickerDefaults.colors(containerColor = TratyTheme.colors.surface),
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Период", modifier = Modifier) },
            modifier = Modifier.height(500.dp),
            colors = DatePickerDefaults.colors(
                containerColor = TratyTheme.colors.surface,
                titleContentColor = TratyTheme.colors.onSurface,
                headlineContentColor = TratyTheme.colors.onSurface,
                selectedDayContainerColor = TratyTheme.colors.primary,
                selectedDayContentColor = TratyTheme.colors.onPrimary,
                todayDateBorderColor = TratyTheme.colors.primary,
                todayContentColor = TratyTheme.colors.primary,
                dayInSelectionRangeContainerColor = TratyTheme.colors.primaryContainer,
                dayInSelectionRangeContentColor = TratyTheme.colors.onSurface,
            ),
        )
    }
}
