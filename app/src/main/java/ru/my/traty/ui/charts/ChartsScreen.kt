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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.Income
import ru.my.traty.data.Places
import ru.my.traty.data.isUserEditable
import ru.my.traty.ui.charts.components.BalanceAreaChart
import ru.my.traty.ui.charts.components.ChartDateCard
import ru.my.traty.ui.charts.components.ChartKpiCard
import ru.my.traty.ui.charts.components.DateRangePickerModal
import ru.my.traty.ui.charts.components.DebitCreditBarChart
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyIconButton
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
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }
    // Категории, раскрытые до магазинов
    var expandedCats by remember { mutableStateOf(emptySet<Long>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        // Шапка и переключатель периодов
        item {
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                text = "Финансовая аналитика",
                style = TratyTheme.typography.headlineLarge,
                color = TratyTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Чипсы выбора периода (прокручиваются: «Даты» — выбор произвольного диапазона)
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                ChartPeriod.entries.forEach { p ->
                    val isSelected = state.period == p
                    Surface(
                        shape = TratyTheme.shapes.small,
                        color = if (isSelected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                        border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
                        modifier = Modifier.clickable {
                            if (p == ChartPeriod.CUSTOM) showRangePicker = true else vm.setPeriod(p)
                        },
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
        item {
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
        item {
            ChartKpiCard(summary = state.summary)
        }

        // График 1: Динамика баланса (Линейный с заливкой)
        item {
            BalanceAreaChart(points = state.balancePoints)
        }

        // График 2: Дебет vs Кредит (Столбчатый)
        item {
            DebitCreditBarChart(bars = state.debitCreditBars)
        }

        // Структура трат (Кредит по категориям)
        if (state.categoryShares.isNotEmpty()) {
            item {
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
                                text = "Структура расходов (Кредит)",
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

        // Траты по магазинам (все категории вместе)
        if (state.topStores.isNotEmpty()) {
            item {
                val credit = state.summary.totalCreditKopecks
                TratyCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surface,
                    shape = TratyTheme.shapes.large,
                ) {
                    Column(Modifier.padding(TratyTheme.spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = TratyTheme.colors.expense,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(TratyTheme.spacing.xs))
                            Text(text = "Траты по магазинам", style = TratyTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(TratyTheme.spacing.md))

                        state.topStores.take(10).forEach { st ->
                            val frac = if (credit > 0) st.totalKopecks.toFloat() / credit else 0f
                            Column(Modifier.padding(vertical = TratyTheme.spacing.xxs)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = st.name,
                                        style = TratyTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${(frac * 100).roundToInt()}%",
                                        style = TratyTheme.typography.labelSmall,
                                        color = TratyTheme.colors.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                                    TratyAmountText(
                                        amountKopecks = st.totalKopecks,
                                        size = AmountSize.SMALL,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                TratyProgressBar(
                                    progress = frac,
                                    color = TratyTheme.colors.expense,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Блок последних доходов периода
        if (state.recentIncomes.isNotEmpty()) {
            item {
                TratyCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surface,
                    shape = TratyTheme.shapes.large,
                ) {
                    Column(Modifier.padding(TratyTheme.spacing.md)) {
                        Text(
                            text = "Поступления периода (Дебет)",
                            style = TratyTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(TratyTheme.spacing.sm))

                        state.recentIncomes.forEach { inc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { incomeToDelete = inc }
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

        item {
            Spacer(Modifier.height(TratyTheme.spacing.lg))
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

    incomeToDelete?.let { inc ->
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            shape = TratyTheme.shapes.large,
            containerColor = TratyTheme.colors.surface,
            title = { Text("Удалить доход?", style = TratyTheme.typography.titleMedium) },
            text = {
                Text(
                    "${inc.source}: +${formatRub(inc.amountKopecks)}\n${inc.note ?: ""}",
                    style = TratyTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                if (inc.isUserEditable) {
                    TratyTextButton(
                        onClick = {
                            vm.deleteIncome(inc)
                            incomeToDelete = null
                        },
                        contentColor = TratyTheme.colors.expense,
                    ) {
                        Text("Удалить")
                    }
                } else {
                    Text(
                        "Запись сверки",
                        style = TratyTheme.typography.labelLarge,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            },
            dismissButton = {
                TratyTextButton(onClick = { incomeToDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}
