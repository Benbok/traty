package ru.my.traty.ui.charts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import ru.my.traty.ui.pluralRu

/**
 * Карточка периода и календаря анализа на странице «Финансовая аналитика и Графики».
 * Показывает начальную и конечную дату анализа, количество дней,
 * позволяет быстро переключать периоды (для месяца) или открывать
 * календарь дат для выставления начальной и конечной даты анализа.
 */
@Composable
fun ChartDateCard(
    period: ChartPeriod,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    billingPeriod: BillingPeriod,
    periodStartDay: Int,
    onPrevBillingPeriod: () -> Unit,
    onNextBillingPeriod: () -> Unit,
    onOpenCalendar: () -> Unit,
    onResetToMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysCount = (ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1).coerceAtLeast(1L)
    val isCustom = period == ChartPeriod.CUSTOM
    val isMonth = period == ChartPeriod.MONTH

    TratyCard(
        modifier = modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.large,
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TratyTheme.spacing.md),
        ) {
            // Верхняя строка: заголовок периода и кнопка вызова календаря
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(TratyTheme.shapes.small)
                        .clickable(onClick = onOpenCalendar)
                        .padding(vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = TratyTheme.colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Text(
                        text = if (isMonth) {
                            formatPeriod(billingPeriod, periodStartDay)
                        } else {
                            period.title
                        },
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TratyTheme.colors.onSurface,
                    )
                }

                Surface(
                    shape = TratyTheme.shapes.small,
                    color = if (isCustom) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                    border = if (isCustom) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
                    modifier = Modifier.clickable(onClick = onOpenCalendar),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditCalendar,
                            contentDescription = "Выбрать даты в календаре",
                            tint = if (isCustom) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isCustom) "Свой период" else "Выбрать даты",
                            style = TratyTheme.typography.labelSmall,
                            color = if (isCustom) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.sm))

            // Основной блок с кнопками навигации (для месяца) и карточками дат
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMonth) {
                    TratyIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий период",
                        onClick = onPrevBillingPeriod,
                        containerColor = TratyTheme.colors.surfaceContainer,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                }

                // Кликабельная карточка диапазона дат: Начальная дата -> Конечная дата
                Surface(
                    shape = TratyTheme.shapes.medium,
                    color = TratyTheme.colors.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(TratyTheme.shapes.medium)
                        .clickable(onClick = onOpenCalendar),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Начальная дата
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Начало",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                            Text(
                                text = formatDate(rangeStart),
                                style = TratyTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TratyTheme.colors.onSurface,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TratyTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )

                        // Конечная дата
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Конец",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                            Text(
                                text = formatDate(rangeEnd),
                                style = TratyTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TratyTheme.colors.onSurface,
                            )
                        }
                    }
                }

                if (isMonth) {
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    TratyIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий период",
                        onClick = onNextBillingPeriod,
                        containerColor = TratyTheme.colors.surfaceContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.xs))

            // Нижняя информационная строка: кол-во дней анализа и кнопка сброса (если выбран свой период)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "За $daysCount ${pluralRu(daysCount, "день", "дня", "дней")}",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )

                if (isCustom) {
                    Text(
                        text = "Сбросить на месяц ✕",
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.primary,
                        modifier = Modifier
                            .clip(TratyTheme.shapes.small)
                            .clickable(onClick = onResetToMonth)
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
