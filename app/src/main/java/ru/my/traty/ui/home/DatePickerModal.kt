package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Модальный диалог выбора конкретной даты через системный Material 3 календарь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember(initialDate) {
        (initialDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TratyTextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedLocalDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onDateSelected(selectedLocalDate)
                    }
                    onDismiss()
                },
                contentColor = TratyTheme.colors.primary,
            ) {
                Text("Выбрать")
            }
        },
        dismissButton = {
            Row {
                TratyTextButton(
                    onClick = {
                        onDateSelected(LocalDate.now())
                        onDismiss()
                    },
                ) {
                    Text("Сегодня")
                }
                TratyTextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        },
        shape = TratyTheme.shapes.large,
        colors = DatePickerDefaults.colors(
            containerColor = TratyTheme.colors.surface,
        ),
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = TratyTheme.colors.surface,
                titleContentColor = TratyTheme.colors.onSurface,
                headlineContentColor = TratyTheme.colors.onSurface,
                selectedDayContainerColor = TratyTheme.colors.primary,
                selectedDayContentColor = TratyTheme.colors.onPrimary,
                todayDateBorderColor = TratyTheme.colors.primary,
                todayContentColor = TratyTheme.colors.primary,
            ),
        )
    }
}
