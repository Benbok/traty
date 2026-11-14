package ru.my.traty.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.LocalDate

/** Подпись под полосой плана: что осталось, сколько можно тратить в день, перерасход или прогноз. */
fun planStatusText(p: PlanProgress, period: BillingPeriod, today: LocalDate = LocalDate.now()): String {
    val future = today.isBefore(period.startDate)
    val past = today.isAfter(period.endDate)
    return when {
        future -> "План на будущий период"
        p.status == PlanStatus.OVER -> "Перерасход ${formatRub(-p.remaining)}"
        past -> if (p.remaining == 0L) "План выбран ровно" else "Уложились, осталось ${formatRub(p.remaining)}"
        p.status == PlanStatus.AT_RISK && p.forecast != null ->
            "По темпу к концу ≈ ${formatRub(p.forecast)}, это больше плана на ${formatRub(p.forecast - p.planned)}"
        p.remaining == 0L -> "План выбран, тратить больше не стоит"
        p.perDay != null -> "Осталось ${formatRub(p.remaining)} · можно ${formatRub(p.perDay)} в день"
        else -> "Осталось ${formatRub(p.remaining)}"
    }
}

@Composable
fun planColor(status: PlanStatus): Color = when (status) {
    PlanStatus.OK -> TratyTheme.colors.primary
    PlanStatus.NEAR, PlanStatus.AT_RISK -> TratyTheme.colors.warning
    PlanStatus.OVER -> TratyTheme.colors.expense
}

/** Строка плана: название, «потрачено из плана», полоса и подпись. Используется на экране плана и в Статистике. */
@Composable
fun PlanProgressRow(item: PlanItem, period: BillingPeriod, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    val p = item.progress
    val color = planColor(p.status)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            Text("${item.emoji} ${item.title}", style = TratyTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${formatRub(p.spent)} из ${formatRub(p.planned)}",
                style = TratyTheme.typography.bodyMedium,
                color = if (p.status == PlanStatus.OVER) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
            )
        }
        Spacer(Modifier.height(TratyTheme.spacing.xxs))
        TratyProgressBar(progress = p.fraction, color = color)
        Spacer(Modifier.height(TratyTheme.spacing.xxs))
        Text(
            planStatusText(p, period, today),
            style = TratyTheme.typography.bodySmall,
            color = if (p.status == PlanStatus.OK) TratyTheme.colors.onSurfaceVariant else color,
        )
    }
}

/**
 * Карточка плана периода для Статистики и Трат.
 * [compact] (Траты): итог и только категории, где план под угрозой, не больше трёх; иначе — все планы.
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
    val shown = if (compact) items.filter { it.progress.status != PlanStatus.OK }.take(3) else items
    val over = items.count { it.progress.status == PlanStatus.OVER }
    TratyCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("План трат", style = TratyTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${formatRub(totals.spent)} из ${formatRub(totals.planned)}",
                    style = TratyTheme.typography.bodyMedium,
                    color = if (totals.spent > totals.planned) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
                )
            }
            Text(
                when {
                    totals.spent > totals.planned -> "Перерасход ${formatRub(totals.spent - totals.planned)}"
                    over > 0 -> "Осталось ${formatRub(totals.planned - totals.spent)}, но в $over ${categoriesWord(over)} перерасход"
                    else -> "Осталось ${formatRub(totals.planned - totals.spent)}"
                },
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            if (shown.isEmpty() && compact) {
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Все категории идут по плану",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.income,
                )
            }
            shown.forEach { item ->
                Spacer(Modifier.height(TratyTheme.spacing.md))
                PlanProgressRow(item, period, today = today)
            }
            if (compact && items.size > shown.size) {
                TratyTextButton(onClick = onOpen) { Text("Весь план") }
            }
        }
    }
}

/** «в 1 категории», «в 2 категориях». */
internal fun categoriesWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "категории" else "категориях"

/** Строка под категорией в Статистике: «План 5 000 ₽ · осталось 1 200 ₽». */
fun planLineText(p: PlanProgress): String =
    if (p.remaining >= 0) "План ${formatRub(p.planned)} · осталось ${formatRub(p.remaining)}"
    else "План ${formatRub(p.planned)} · перерасход ${formatRub(-p.remaining)}"
