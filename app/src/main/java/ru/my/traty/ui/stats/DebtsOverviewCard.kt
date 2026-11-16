package ru.my.traty.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.debt.DebtOverview
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalDate

/** Сколько покупок показывать в карточке; остальные — на экране «Покупки частями». */
private const val MAX_ROWS = 5

/**
 * Карточка «Долги по взносам» в ленте «Статистики». Долг считается на сегодня, поэтому карточка одна и та же
 * в режимах Период / День / Неделя / Даты. Пока данные не загрузились, её нет, чтобы не мигало «долгов нет».
 */
internal fun LazyListScope.debtsOverviewItem(vm: StatsDebtsViewModel, onOpenDebts: () -> Unit, onOpenDebt: (Long) -> Unit) {
    item(key = "debts", contentType = "debts") {
        val overview by vm.overview.collectAsStateWithLifecycle()
        val categories by vm.categories.collectAsStateWithLifecycle()
        overview?.let { DebtsOverviewCard(it, categories, onOpenDebts, onOpenDebt) }
    }
}

@Composable
private fun DebtsOverviewCard(
    o: DebtOverview,
    categories: Map<Long, DebtCategoryLabel>,
    onOpenDebts: () -> Unit,
    onOpenDebt: (Long) -> Unit,
) {
    val today = LocalDate.now()
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        onClick = if (o.isEmpty) onOpenDebts else null,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            DebtsHeader(subtitle = if (o.isEmpty) null else DebtOverviewTexts.subtitle(o), onOpenDebts = onOpenDebts)
            if (o.isEmpty) {
                Text(
                    DebtOverviewTexts.empty(o.closedCount),
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                return@Column
            }
            DebtsTotals(o)
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            o.lines.take(MAX_ROWS).forEach { line ->
                DebtOverviewRow(line, categories[line.debt.categoryId], today, onClick = { onOpenDebt(line.debt.id) })
            }
            val more = o.lines.size - MAX_ROWS
            if (more > 0) {
                TratyTextButton(onClick = onOpenDebts) { Text("Ещё $more — все покупки", style = TratyTheme.typography.labelMedium) }
            }
            DebtsNotices(o)
        }
    }
}

@Composable
private fun DebtsHeader(subtitle: String?, onOpenDebts: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Долги по взносам", style = TratyTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
        TratyTextButton(onClick = onOpenDebts) { Text("Все", style = TratyTheme.typography.labelMedium) }
    }
}

/** «Осталось выплатить», ближайшие 30 дней и просрочка, переплата по кредитам. */
@Composable
private fun DebtsTotals(o: DebtOverview) {
    Text("Осталось выплатить", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
    TratyAmountText(amountKopecks = o.leftToPayKopecks, size = AmountSize.LARGE, fontWeight = FontWeight.Bold)
    Text(DebtOverviewTexts.paidNote(o), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    Spacer(Modifier.height(TratyTheme.spacing.sm))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        DebtMetric("В ближайшие ${o.dueSoonDays} дней", o.dueSoonKopecks, highlight = false, modifier = Modifier.weight(1f))
        DebtMetric("Просрочено", o.overdueKopecks, highlight = o.overdueKopecks > 0, modifier = Modifier.weight(1f))
    }
    DebtOverviewTexts.overpayment(o)?.let {
        Spacer(Modifier.height(TratyTheme.spacing.sm))
        Text(it, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun DebtMetric(label: String, kopecks: Long, highlight: Boolean, modifier: Modifier = Modifier) {
    TratyCard(modifier = modifier, backgroundColor = TratyTheme.colors.surfaceContainer, border = null) {
        Column(Modifier.padding(TratyTheme.spacing.sm)) {
            Text(label, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
            if (kopecks > 0) {
                TratyAmountText(
                    amountKopecks = kopecks,
                    size = AmountSize.SMALL,
                    color = if (highlight) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("нет", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
    }
}
