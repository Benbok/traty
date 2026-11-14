package ru.my.traty.ui.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.data.budget.PlanMonth
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatMonth
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.CategoryPicker

import androidx.compose.material3.Surface

/** Настройки → «План трат»: лимиты по категориям и подкатегориям на расчётный месяц. */
@Composable
fun BudgetScreen(onBack: () -> Unit, vm: BudgetViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val period by vm.period.collectAsStateWithLifecycle()
    val startDay by vm.startDay.collectAsStateWithLifecycle()
    val plans by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val totals = remember(plans) { planTotals(plans) }
    var editing by remember { mutableStateOf<PlanEdit?>(null) }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "План трат",
                subtitle = "Сколько хотите тратить по категориям",
                onBack = onBack,
                actions = {
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Добавить план",
                        onClick = { editing = PlanEdit(key = null, amountKopecks = 0, repeats = true) },
                        containerColor = TratyTheme.colors.primaryContainer,
                        tint = TratyTheme.colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = TratyTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            item {
                TratyMonthPicker(
                    periodLabel = formatPeriod(period, startDay),
                    totalKopecks = totals.planned,
                    onPrev = { vm.shift(-1) },
                    onNext = { vm.shift(1) },
                    deltaText = when {
                        plans.isEmpty() -> "Плана на этот период нет"
                        totals.spent > totals.planned -> "Потрачено ${formatRub(totals.spent)} · перерасход ${formatRub(totals.spent - totals.planned)}"
                        else -> "Потрачено ${formatRub(totals.spent)} · осталось ${formatRub(totals.planned - totals.spent)}"
                    },
                )
            }
            if (plans.isEmpty()) {
                item {
                    TratyEmptyState(
                        icon = Icons.Filled.Flag,
                        title = "Плана пока нет",
                        description = "Нажмите «+», выберите категорию и сумму на месяц. План будет виден в Статистике и в Тратах, " +
                            "а «можно тратить в день» подскажет, сколько осталось до конца периода.",
                    )
                }
            }
            items(plans, key = { "${it.key.categoryId}/${it.key.subcategoryId}" }) { item ->
                TratyCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TratyTheme.spacing.lg),
                    onClick = {
                        val own = vm.ownRow(item.key)
                        editing = PlanEdit(item.key, item.progress.planned, own?.repeats ?: item.progress.plan.repeats)
                    },
                ) {
                    Column(Modifier.padding(TratyTheme.spacing.md)) {
                        PlanProgressRow(item, period)
                        Text(
                            repeatText(item, PlanMonth.of(period)),
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                            // Под текстом строки, а не под значком категории (32 dp значок + отступ)
                            modifier = Modifier.padding(start = 32.dp + TratyTheme.spacing.md),
                        )
                    }
                }
            }
            if (startDay != 1) {
                item {
                    Text(
                        "Месяц плана — ваш расчётный период: ${formatDateSpan(period.startDate, period.endDate)}.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                    )
                }
            }
        }
    }

    editing?.let { edit ->
        Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
            EditPlanScreen(
                edit = edit,
                categories = categories,
                taken = plans.map { it.key }.toSet(),
                monthTitle = formatMonth(PlanMonth.toYearMonth(PlanMonth.of(period))),
                hint = vm::hint,
                onDismiss = { editing = null },
                onSave = { key, amount, repeats ->
                    vm.save(key, amount, repeats)
                    editing = null
                },
                onRemove = { key ->
                    vm.remove(key)
                    editing = null
                },
            )
        }
    }
}

private fun repeatText(item: PlanItem, month: Int): String {
    val plan = item.progress.plan
    return when {
        plan.month != month -> "Повторяется с ${formatMonth(PlanMonth.toYearMonth(plan.month)).lowercase()}"
        plan.repeats -> "Повторяется каждый месяц"
        else -> "Только на этот месяц"
    }
}
