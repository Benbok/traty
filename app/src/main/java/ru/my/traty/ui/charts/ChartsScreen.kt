package ru.my.traty.ui.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
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
import ru.my.traty.ui.charts.components.AddIncomeDialog
import ru.my.traty.ui.charts.components.BalanceAreaChart
import ru.my.traty.ui.charts.components.ChartKpiCard
import ru.my.traty.ui.charts.components.DebitCreditBarChart
import ru.my.traty.ui.charts.model.ChartPeriod
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatMonth
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime
import kotlin.math.roundToInt

@Composable
fun ChartsScreen(vm: ChartsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAddIncome by remember { mutableStateOf(false) }
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        // Шапка и переключатель периодов
        item {
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Финансовая аналитика",
                    style = TratyTheme.typography.headlineLarge,
                    color = TratyTheme.colors.onSurface,
                )
                TratyButton(
                    onClick = { showAddIncome = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Доход")
                }
            }
        }

        // Чипсы выбора периода
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                ChartPeriod.entries.forEach { p ->
                    val isSelected = state.period == p
                    Surface(
                        shape = TratyTheme.shapes.small,
                        color = if (isSelected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                        border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.setPeriod(p) },
                    ) {
                        Text(
                            text = p.title,
                            style = TratyTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Если выбран "Месяц", показываем переключатель месяцев
        if (state.period == ChartPeriod.MONTH) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TratyIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий месяц",
                        onClick = vm::prevMonth,
                        containerColor = TratyTheme.colors.surfaceContainer,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = formatMonth(state.month),
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TratyTheme.colors.onSurface,
                    )
                    TratyIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий месяц",
                        onClick = vm::nextMonth,
                        containerColor = TratyTheme.colors.surfaceContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
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
                            Column(Modifier.padding(vertical = TratyTheme.spacing.xxs)) {
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
                                }
                                TratyProgressBar(
                                    progress = cat.percent,
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

    if (showAddIncome) {
        AddIncomeDialog(
            onDismiss = { showAddIncome = false },
            onSave = { amount, source, note, date ->
                vm.addIncome(amount, source, note, date)
                showAddIncome = false
            },
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
                TratyTextButton(
                    onClick = {
                        vm.deleteIncome(inc)
                        incomeToDelete = null
                    },
                    contentColor = TratyTheme.colors.expense,
                ) {
                    Text("Удалить")
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
