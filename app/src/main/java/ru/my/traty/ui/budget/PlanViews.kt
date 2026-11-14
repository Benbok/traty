package ru.my.traty.ui.budget

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.my.traty.data.budget.PlanOverview
import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import kotlin.math.roundToInt

/** Подпись под полосой плана: что осталось, сколько можно тратить в день, перерасход или прогноз. */
fun planStatusText(p: PlanProgress, period: BillingPeriod, today: LocalDate = LocalDate.now()): String {
    val future = today.isBefore(period.startDate)
    val past = today.isAfter(period.endDate)
    return when {
        future -> "План на будущий период"
        p.status == PlanStatus.OVER -> "Перерасход ${formatRub(-p.remaining)}"
        past -> if (p.remaining == 0L) "План выбран ровно" else "Уложились, осталось ${formatRub(p.remaining)}"
        p.status == PlanStatus.AT_RISK && p.forecast != null ->
            "По темпу к концу ≈ ${formatRub(p.forecast)}, больше плана на ${formatRub(p.forecast - p.planned)}"
        p.remaining == 0L -> "План выбран, тратить больше не стоит"
        p.perDay != null -> "Осталось ${formatRub(p.remaining)} · ${formatRub(p.perDay)} в день"
        else -> "Осталось ${formatRub(p.remaining)}"
    }
}

@Composable
fun planColor(status: PlanStatus): Color = when (status) {
    PlanStatus.OK -> TratyTheme.colors.primary
    PlanStatus.NEAR, PlanStatus.AT_RISK -> TratyTheme.colors.warning
    PlanStatus.OVER -> TratyTheme.colors.expense
}

/** Цвет общей полосы: перерасход — красный, траты обгоняют время — жёлтый, иначе основной. */
@Composable
private fun overviewColor(o: PlanOverview): Color = when {
    o.spent > o.planned -> TratyTheme.colors.expense
    o.elapsed != null && o.elapsed < 1f && o.fraction > o.elapsed + 0.1f -> TratyTheme.colors.warning
    else -> TratyTheme.colors.primary
}

/**
 * Полоса плана с отметкой «сегодня»: тонкая вертикальная черта там, где по времени должна быть трата
 * при ровном темпе. Заливка левее отметки — идём с запасом, правее — тратим быстрее, чем идёт месяц.
 */
@Composable
fun PlanBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marker: Float? = null,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "planBar",
    )
    val markerHeight = height + 6.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(markerHeight), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(TratyTheme.shapes.pill)
                .background(TratyTheme.colors.surfaceContainerHigh),
        ) {
            if (animated > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .clip(TratyTheme.shapes.pill)
                        .background(color),
                )
            }
        }
        if (marker != null && marker > 0f && marker < 1f) {
            Box(
                Modifier
                    .offset(x = maxWidth * marker - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(TratyTheme.shapes.pill)
                    .background(TratyTheme.colors.onSurface.copy(alpha = 0.55f)),
            )
        }
    }
}

/** Значок состояния рядом с подписью: цвет не единственный признак. */
@Composable
private fun StatusIcon(status: PlanStatus, modifier: Modifier = Modifier) {
    val icon = when (status) {
        PlanStatus.OK -> Icons.Filled.CheckCircle
        PlanStatus.NEAR, PlanStatus.AT_RISK -> Icons.Filled.WarningAmber
        PlanStatus.OVER -> Icons.Filled.ErrorOutline
    }
    Icon(icon, contentDescription = null, tint = planColor(status), modifier = modifier.size(14.dp))
}

/** Отметка «сегодня» для полос: только у идущего периода. */
private fun todayMarker(period: BillingPeriod, today: LocalDate): Float? {
    if (today.isBefore(period.startDate) || today.isAfter(period.endDate)) return null
    val total = period.endDate.toEpochDay() - period.startDate.toEpochDay() + 1
    return (today.toEpochDay() - period.startDate.toEpochDay() + 1).toFloat() / total
}

/**
 * Строка плана категории: значок, название, «потрачено / план», полоса с отметкой «сегодня»
 * и подпись состояния со значком. Используется на экране плана и в карточке «План трат».
 */
@Composable
fun PlanProgressRow(
    item: PlanItem,
    period: BillingPeriod,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    showStatus: Boolean = true,
) {
    val p = item.progress
    val color = planColor(p.status)
    val ink = TratyTheme.colors.onSurface
    val muted = TratyTheme.colors.onSurfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        TratyCategoryBadge(emoji = item.emoji, categoryName = item.categoryName, size = 32.dp)
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = TratyTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) {
                            append(formatRub(p.spent))
                        }
                        withStyle(SpanStyle(color = muted)) {
                            append(" / ${formatRub(p.planned)}")
                        }
                    },
                    style = TratyTheme.typography.bodySmall,
                )
            }
            PlanBar(fraction = p.fraction, color = color, marker = todayMarker(period, today), height = 6.dp)
            if (showStatus) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(p.status)
                    Spacer(Modifier.width(TratyTheme.spacing.xxs))
                    Text(
                        planStatusText(p, period, today),
                        style = TratyTheme.typography.bodySmall,
                        color = if (p.status == PlanStatus.OK) TratyTheme.colors.onSurfaceVariant else color,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Карточка плана периода для Статистики и Трат: большая сумма «потрачено из плана», общая полоса
 * с отметкой «сегодня», остаток и «в день», затем категории (сначала самые тревожные).
 * [compact] (Траты): не больше [COMPACT_ROWS] категорий, у спокойных — без подписи.
 */
@Composable
fun PlanSummaryCard(
    items: List<PlanItem>,
    period: BillingPeriod,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    today: LocalDate = LocalDate.now(),
) {
    val totals = planTotals(items)
    val o = PlanOverview.of(totals.planned, totals.spent, period, today)
    val marker = todayMarker(period, today)
    val shown = if (compact) items.take(COMPACT_ROWS) else items
    val hidden = items.size - shown.size
    val troubled = items.count { it.progress.status != PlanStatus.OK }

    TratyCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "План трат",
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(o.fraction * 100).roundToInt()} %",
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Открыть план",
                    tint = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Row {
                Text(
                    formatRub(o.spent),
                    style = TratyTheme.typography.largeAmount,
                    color = TratyTheme.colors.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(TratyTheme.spacing.xs))
                Text(
                    "из ${formatRub(o.planned)}",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            PlanBar(fraction = o.fraction, color = overviewColor(o), marker = marker, height = 10.dp)
            Spacer(Modifier.height(TratyTheme.spacing.xxs))
            Row {
                Text(
                    when {
                        o.remaining < 0 -> "Перерасход ${formatRub(-o.remaining)}"
                        o.perDay != null -> "Осталось ${formatRub(o.remaining)} · ${formatRub(o.perDay!!)} в день"
                        else -> "Осталось ${formatRub(o.remaining)}"
                    },
                    style = TratyTheme.typography.bodySmall,
                    color = if (o.remaining < 0) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (marker != null) {
                    Text(
                        "${o.daysLeft} ${daysWord(o.daysLeft)} до конца",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
            if (troubled == 0 && items.isNotEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(PlanStatus.OK)
                    Spacer(Modifier.width(TratyTheme.spacing.xxs))
                    Text(
                        "Все категории идут по плану",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.md))
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            shown.forEach { item ->
                Spacer(Modifier.height(TratyTheme.spacing.md))
                PlanProgressRow(
                    item,
                    period,
                    today = today,
                    showStatus = !compact || item.progress.status != PlanStatus.OK,
                )
            }
            if (hidden > 0) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                Text(
                    "Ещё $hidden ${categoriesCountWord(hidden)} · весь план",
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.primary,
                )
            }
            if (marker != null) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(10.dp)
                            .background(TratyTheme.colors.onSurface.copy(alpha = 0.55f)),
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Text(
                        "сегодня — столько можно было потратить при ровном темпе",
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** В компактной карточке (Траты) — не больше стольких категорий. */
const val COMPACT_ROWS = 3

/** «1 день», «2 дня», «5 дней». */
internal fun daysWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "дней"
        m10 == 1 -> "день"
        m10 in 2..4 -> "дня"
        else -> "дней"
    }
}

/** «ещё 1 категория», «ещё 2 категории», «ещё 5 категорий». */
internal fun categoriesCountWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "категорий"
        m10 == 1 -> "категория"
        m10 in 2..4 -> "категории"
        else -> "категорий"
    }
}

/** «в 1 категории», «в 2 категориях». */
internal fun categoriesWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "категории" else "категориях"

/** Строка под категорией в Статистике: «План 5 000 ₽ · осталось 1 200 ₽». */
fun planLineText(p: PlanProgress): String =
    if (p.remaining >= 0) "План ${formatRub(p.planned)} · осталось ${formatRub(p.remaining)}"
    else "План ${formatRub(p.planned)} · перерасход ${formatRub(-p.remaining)}"
