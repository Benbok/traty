package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatMonth
import java.time.LocalDate
import java.time.YearMonth

/**
 * Заголовочный компонент переключения месяца/дня и отображения итоговой суммы.
 * Поддерживает выбор конкретной даты в календаре при нажатии на заголовок даты.
 */
@Composable
fun TratyMonthPicker(
    month: YearMonth,
    totalKopecks: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    deltaText: String? = null,
    selectedDate: LocalDate? = null,
    onDateClick: (() -> Unit)? = null,
    onClearDate: (() -> Unit)? = null,
) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        shape = TratyTheme.shapes.large,
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TratyIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = if (selectedDate != null) "Предыдущий день" else "Предыдущий месяц",
                onClick = onPrev,
                containerColor = TratyTheme.colors.surfaceContainer,
                modifier = Modifier.size(40.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(TratyTheme.shapes.small)
                        .clickable(enabled = onDateClick != null) { onDateClick?.invoke() }
                        .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xxs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Выбрать дату в календаре",
                        tint = if (selectedDate != null) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Text(
                        text = if (selectedDate != null) formatDate(selectedDate) else formatMonth(month),
                        style = TratyTheme.typography.titleSmall,
                        color = if (selectedDate != null) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                    )
                    if (onDateClick != null) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                TratyAmountText(
                    amountKopecks = totalKopecks,
                    size = AmountSize.LARGE,
                    color = TratyTheme.colors.onSurface,
                )

                if (selectedDate != null && onClearDate != null) {
                    Text(
                        text = "Показать весь месяц ✕",
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.primary,
                        modifier = Modifier
                            .clip(TratyTheme.shapes.small)
                            .clickable(onClick = onClearDate)
                            .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    )
                } else if (!deltaText.isNullOrBlank()) {
                    Text(
                        text = deltaText,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }

            TratyIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (selectedDate != null) "Следующий день" else "Следующий месяц",
                onClick = onNext,
                containerColor = TratyTheme.colors.surfaceContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
