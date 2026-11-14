package ru.my.traty.ui.charts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Диалог выбора периода «с даты по дату» (Material 3 календарь с диапазоном).
 * Содержит карточку выбранных начальной и конечной дат анализа,
 * быстрые пресеты периодов и интерактивный календарь.
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

    val daysCount = if (start != null && end != null) {
        (ChronoUnit.DAYS.between(start, end) + 1).coerceAtLeast(1L)
    } else null

    val today = LocalDate.now()
    val presets = listOf(
        "30 дней" to (today.minusDays(29) to today),
        "Этот месяц" to (today.withDayOfMonth(1) to today),
        "Прошлый месяц" to (
            today.minusMonths(1).withDayOfMonth(1) to
                today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())
        ),
        "90 дней" to (today.minusDays(89) to today),
        "С начала года" to (LocalDate.of(today.year, 1, 1) to today),
    )

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.xs),
        ) {
            // Карточка выбранных начальной и конечной дат анализа
            TratyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.medium,
                backgroundColor = TratyTheme.colors.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(TratyTheme.spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = TratyTheme.colors.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Период анализа",
                                style = TratyTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TratyTheme.colors.onSurface,
                            )
                        }
                        if (daysCount != null) {
                            Text(
                                text = "$daysCount ${daysRussianWord(daysCount)}",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(TratyTheme.spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Начальная дата анализа
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Начальная дата",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                            Text(
                                text = if (start != null) formatDate(start) else "Выберите дату",
                                style = TratyTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (start != null) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TratyTheme.colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )

                        // Конечная дата анализа
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Конечная дата",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                            Text(
                                text = if (end != null) formatDate(end) else "Выберите дату",
                                style = TratyTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (end != null) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.xs))

            // Чипсы быстрых пресетов периода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                presets.forEach { (title, range) ->
                    val isCurrentPreset = start == range.first && end == range.second
                    Surface(
                        shape = TratyTheme.shapes.small,
                        color = if (isCurrentPreset) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                        border = if (isCurrentPreset) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
                        modifier = Modifier.clickable {
                            val pStart = range.first.toPickerMillis()
                            val pEnd = range.second.toPickerMillis()
                            state.setSelection(pStart, pEnd)
                            state.displayedMonthMillis = pStart
                        },
                    ) {
                        Text(
                            text = title,
                            style = TratyTheme.typography.labelSmall,
                            fontWeight = if (isCurrentPreset) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrentPreset) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.xxs))

            DateRangePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                modifier = Modifier.height(370.dp),
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
}

private fun daysRussianWord(count: Long): String {
    val c = count % 100
    val last = c % 10
    return when {
        c in 11..19 -> "дней"
        last == 1L -> "день"
        last in 2..4 -> "дня"
        else -> "дней"
    }
}
