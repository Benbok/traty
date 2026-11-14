package ru.my.traty.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.designsystem.components.TratyProgressBar
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
