package ru.my.traty.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.data.budget.Promised
import ru.my.traty.data.budget.PromisedItem
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateNumeric
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/**
 * «Уже обещано до конца периода»: платежи по рассрочкам, кредитам и подпискам, которые ещё спишутся,
 * и сколько остаётся свободным после них. По нажатию раскрывается список платежей.
 * Ничего не показывает, если обещанного нет.
 */
@Composable
internal fun PromisedBlock(promised: Promised, remaining: Long, period: BillingPeriod) {
    if (promised.isEmpty) return
    var expanded by rememberSaveable(period.startDate.toEpochDay()) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = TratyTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
    ) {
        Text(
            promisedSummary(promised),
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        val free = remaining - promised.totalKopecks
        Text(
            promisedFreeText(free, promised.approximate),
            style = TratyTheme.typography.bodySmall,
            color = if (free < 0) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
        )
        if (expanded) promised.items.forEach { PromisedRow(it) }
    }
}

@Composable
private fun PromisedRow(item: PromisedItem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            promisedRowTitle(item),
            style = TratyTheme.typography.labelSmall,
            color = if (item.overdue) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
        )
        Text(
            (if (item.approximate) "≈ " else "") + formatRub(item.amountKopecks),
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

internal fun promisedSummary(p: Promised): String {
    val n = p.items.size
    val approx = if (p.approximate) "около " else ""
    return "Уже обещано до конца периода: $approx${formatRub(p.totalKopecks)} · $n ${pluralRu(n.toLong(), "платёж", "платежа", "платежей")}"
}

internal fun promisedFreeText(free: Long, approximate: Boolean): String {
    val approx = if (approximate) "около " else ""
    return if (free < 0) "С учётом обещанного не хватит $approx${formatRub(-free)}" else "С учётом обещанного свободно $approx${formatRub(free)}"
}

internal fun promisedRowTitle(item: PromisedItem): String =
    "${item.title} · ${if (item.overdue) "просрочен, срок " else ""}${formatDateNumeric(item.date)}"
