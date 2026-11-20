package ru.my.traty.ui.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.Places
import ru.my.traty.ui.charts.components.CashFlowChart
import ru.my.traty.ui.charts.components.ChartDateCard
import ru.my.traty.ui.charts.components.ChartKpiCard
import ru.my.traty.ui.charts.components.DateRangePickerModal
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime
import kotlin.math.roundToInt

@Composable
fun ChartsScreen(vm: ChartsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    // Категории, раскрытые до магазинов
    var expandedCats by remember { mutableStateOf(emptySet<Long>()) }

    Column(Modifier.fillMaxSize()) {
        TratyHeaderCard(
            title = "Графики",
            subtitle = "Финансовая аналитика и динамика",
            icon = Icons.Filled.ShowChart,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            // Чипсы выбора периода (Месяц, 3 мес, 6 мес, Год, Всё время)
        item(key = "period-chips", contentType = "period-chips") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                ChartPeriod.entries.filter { it != ChartPeriod.CUSTOM }.forEach { p ->
                    val isSelected = state.period == p
                    Surface(
                        shape = TratyTheme.shapes.small,
                        color = if (isSelected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                        border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
                        modifier = Modifier.clip(TratyTheme.shapes.small).clickable { vm.setPeriod(p) },
                    ) {
                        Text(
                            text = p.title,
                            style = TratyTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Карточка календаря и периода анализа (начальная и конечная даты, переключение, вызов календаря)
        item(key = "date-card", contentType = "date-card") {
            ChartDateCard(
                period = state.period,
                rangeStart = state.rangeStart,
                rangeEnd = state.rangeEnd,
                billingPeriod = state.billingPeriod,
                periodStartDay = state.periodStartDay,
                onPrevBillingPeriod = vm::prevBillingPeriod,
                onNextBillingPeriod = vm::nextBillingPeriod,
                onOpenCalendar = { showRangePicker = true },
                onResetToMonth = { vm.setPeriod(ChartPeriod.MONTH) },
            )
        }

        // Сводные метрики (Сальдо, Дебет, Кредит, Сбережения)
        item(key = "kpi", contentType = "kpi") {
            ChartKpiCard(summary = state.summary)
        }

        // Накопительные доходы и расходы на общей шкале.
        item(key = "cash-flow-chart", contentType = "cash-flow-chart") {
            CashFlowChart(points = state.cashFlowPoints)
        }

        // Структура трат (Кредит по категориям)
        if (state.categoryShares.isNotEmpty()) {
            item(key = "category-shares", contentType = "category-shares") {
                TratyCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surface,
                    shape = TratyTheme.shapes.large,
                ) {
                    Column(Modifier.padding(TratyTheme.spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PieChart,
                                contentDescription = null,
                                tint = TratyTheme.colors.expense,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(TratyTheme.spacing.xs))
                            Text(
                                text = "На что ушли деньги",
                                style = TratyTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(TratyTheme.spacing.md))

                        state.categoryShares.forEach { cat ->
                            val expandable = Places.hasNamed(cat.stores)
                            val isExpanded = expandable && cat.categoryId in expandedCats
                            Column(
                                Modifier
                                    .then(
                                        if (expandable) Modifier.clickable {
                                            expandedCats = if (cat.categoryId in expandedCats) expandedCats - cat.categoryId
                                            else expandedCats + cat.categoryId
                                        } else Modifier
                                    )
                                    .padding(vertical = TratyTheme.spacing.xxs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TratyCategoryBadge(
                                        emoji = cat.emoji,
                                        categoryName = cat.name,
                                        size = 30.dp,
                                    )
                                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                                    Text(
                                        text = cat.name,
                                        style = TratyTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${(cat.percent * 100).roundToInt()}%",
                                        style = TratyTheme.typography.labelSmall,
                                        color = TratyTheme.colors.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                                    TratyAmountText(
                                        amountKopecks = cat.totalKopecks,
                                        size = AmountSize.SMALL,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (expandable) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (isExpanded) "Скрыть магазины" else "Показать магазины",
                                            tint = TratyTheme.colors.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 2.dp).size(18.dp),
                                        )
                                    }
                                }
                                TratyProgressBar(
                                    progress = cat.percent,
                                    color = TratyTheme.colors.expense,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (isExpanded) {
                                    cat.stores.forEach { st ->
                                        val frac = if (cat.totalKopecks > 0) st.totalKopecks.toFloat() / cat.totalKopecks else 0f
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = TratyTheme.spacing.xs),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = st.name,
                                                style = TratyTheme.typography.bodySmall,
                                                color = if (st.isNoPlace) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${(frac * 100).roundToInt()}%",
                                                style = TratyTheme.typography.labelSmall,
                                                color = TratyTheme.colors.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(TratyTheme.spacing.sm))
                                            TratyAmountText(amountKopecks = st.totalKopecks, size = AmountSize.SMALL)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Блок последних доходов периода
        if (state.recentIncomes.isNotEmpty()) {
            item(key = "recent-incomes", contentType = "recent-incomes") {
                TratyCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surface,
                    shape = TratyTheme.shapes.large,
                ) {
                    Column(Modifier.padding(TratyTheme.spacing.md)) {
                        Text(
                            text = "Доходы за период",
                            style = TratyTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(TratyTheme.spacing.sm))

                        state.recentIncomes.forEach { inc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = TratyTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = inc.source,
                                        style = TratyTheme.typography.titleSmall,
                                        color = TratyTheme.colors.onSurface,
                                    )
                                    val sub = listOfNotNull(formatTime(inc.timestamp), inc.note).joinToString(" · ")
                                    Text(
                                        text = sub,
                                        style = TratyTheme.typography.bodySmall,
                                        color = TratyTheme.colors.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "+${formatRub(inc.amountKopecks)}",
                                    style = TratyTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TratyTheme.colors.income,
                                )
                            }
                            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                        }
                    }
                }
            }
        }

        item(key = "bottom-spacer", contentType = "bottom-spacer") {
            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }
}

    if (showRangePicker) {
        DateRangePickerModal(
            initialStart = state.rangeStart,
            initialEnd = state.rangeEnd,
            onRangeSelected = vm::setCustomRange,
            onDismiss = { showRangePicker = false },
        )
    }
}

