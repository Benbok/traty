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

/** Что открыто в диалоге плана: новый план или правка существующего. */
private data class PlanEdit(val key: PlanKey?, val amountKopecks: Long, val repeats: Boolean)

/** Настройки → «План трат»: лимиты по категориям и подкатегориям на расчётный месяц. */
@Composable
fun BudgetScreen(onBack: () -> Unit, onOpenWeekly: () -> Unit = {}, vm: BudgetViewModel = viewModel()) {
    // Системная «Назад» возвращает на прошлый экран, а не закрывает приложение
    BackHandler(onBack = onBack)
    val period by vm.period.collectAsStateWithLifecycle()
    val startDay by vm.startDay.collectAsStateWithLifecycle()
    val plans by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val alertsOn by vm.alertsEnabled.collectAsStateWithLifecycle()
    val categoryTotals = remember(plans) { planTotals(plans) }
    // С общим лимитом шапка показывает его и все траты месяца, без него — сумму планов категорий
    val totals = total?.let { PlanTotals(it.planned, it.spent) } ?: categoryTotals
    var editing by remember { mutableStateOf<PlanEdit?>(null) }
    var editingTotal by remember { mutableStateOf(false) }

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
                        plans.isEmpty() && total == null -> "Плана на этот период нет"
                        totals.spent > totals.planned -> "Потрачено ${formatRub(totals.spent)} · перерасход ${formatRub(totals.spent - totals.planned)}"
                        else -> "Потрачено ${formatRub(totals.spent)} · осталось ${formatRub(totals.planned - totals.spent)}"
                    },
                )
            }
            item {
                TotalLimitCard(
                    total = total,
                    categoriesPlanned = categoryTotals.planned,
                    period = period,
                    onClick = { editingTotal = true },
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
            }
            if (plans.isEmpty()) {
                item {
                    TratyEmptyState(
                        icon = Icons.Filled.Flag,
                        title = "Планов по категориям пока нет",
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
            item {
                WeeklyTasksEntryCard(onOpenWeekly, Modifier.padding(horizontal = TratyTheme.spacing.lg))
            }
            item {
                BudgetAlertsCard(
                    enabled = alertsOn,
                    hasPlans = plans.isNotEmpty() || total != null,
                    onChange = vm::setAlertsEnabled,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
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

    if (editingTotal) {
        TotalLimitDialog(
            current = total,
            repeatsNow = vm.ownTotalRepeats() ?: total?.limit?.repeats ?: true,
            monthTitle = formatMonth(PlanMonth.toYearMonth(PlanMonth.of(period))).lowercase(),
            onDismiss = { editingTotal = false },
            onSave = { amount, repeats ->
                vm.saveTotal(amount, repeats)
                editingTotal = false
            },
            onRemove = {
                vm.removeTotal()
                editingTotal = false
            },
        )
    }

    editing?.let { edit ->
        PlanEditDialog(
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

private fun repeatText(item: PlanItem, month: Int): String {
    val plan = item.progress.plan
    return when {
        plan.month != month -> "Повторяется с ${formatMonth(PlanMonth.toYearMonth(plan.month)).lowercase()}"
        plan.repeats -> "Повторяется каждый месяц"
        else -> "Только на этот месяц"
    }
}

@Composable
private fun PlanEditDialog(
    edit: PlanEdit,
    categories: List<CategoryWithSubs>,
    taken: Set<PlanKey>,
    monthTitle: String,
    hint: (PlanKey) -> Long?,
    onDismiss: () -> Unit,
    onSave: (PlanKey, Long, Boolean) -> Unit,
    onRemove: (PlanKey) -> Unit,
) {
    val isNew = edit.key == null
    var categoryName by remember {
        mutableStateOf(
            categories.firstOrNull { it.category.id == edit.key?.categoryId }?.category?.name
                ?: categories.firstOrNull()?.category?.name.orEmpty()
        )
    }
    val category = categories.firstOrNull { it.category.name == categoryName }
    var subId by remember { mutableStateOf(edit.key?.subcategoryId) }
    var amount by remember { mutableStateOf(if (edit.amountKopecks > 0) AmountInput.fromKopecks(edit.amountKopecks) else "") }
    var repeats by remember { mutableStateOf(edit.repeats) }
    val key = category?.let { PlanKey(it.category.id, subId?.takeIf { id -> it.subs.any { s -> s.id == id } }) }
    val suggestion = remember(key) { key?.let(hint) }
    val duplicate = isNew && key != null && key in taken

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text(if (isNew) "План на ${monthTitle.lowercase()}" else "План · $monthTitle", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                if (isNew) {
                    CategoryPicker(categoryName, categories, null) {
                        categoryName = it
                        subId = null
                    }
                    if (category != null && category.subs.isNotEmpty()) {
                        SubcategoryPicker(category, subId) { subId = it }
                    }
                } else {
                    Text(
                        "${category?.category?.emoji.orEmpty()} $categoryName" +
                            (category?.subs?.firstOrNull { it.id == subId }?.let { " · ${it.name}" } ?: ""),
                        style = TratyTheme.typography.titleSmall,
                    )
                }
                TratyAmountTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Сумма на месяц",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestion != null) {
                    TratyTextButton(onClick = { amount = (suggestion / 100).toString() }) {
                        Text("Подсказка: ${formatRub(suggestion)} (средняя за 3 месяца − 10 %)")
                    }
                }
                FilterChip(
                    selected = repeats,
                    onClick = { repeats = !repeats },
                    label = { Text("Повторять каждый месяц") },
                )
                if (duplicate) {
                    Text(
                        "План на эту категорию уже есть, сохранение заменит его сумму.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.warning,
                    )
                }
                if (!isNew) {
                    TratyTextButton(onClick = { edit.key?.let(onRemove) }) {
                        Text("Убрать план с этого месяца", color = TratyTheme.colors.expense)
                    }
                }
            }
        },
        confirmButton = {
            val kopecks = AmountInput.parsePositiveKopecks(amount)
            TratyButton(
                enabled = key != null && kopecks != null,
                onClick = {
                    if (key != null && kopecks != null) onSave(key, kopecks, repeats)
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор подкатегории; «Вся категория» — план на категорию целиком. */
@Composable
private fun SubcategoryPicker(category: CategoryWithSubs, selected: Long?, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val subs = category.subs.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    val label = subs.firstOrNull { it.id == selected }?.name ?: "Вся категория"
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = TratyTheme.shapes.small) {
            Text(label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Вся категория") }, onClick = { onPick(null); open = false })
            subs.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { onPick(s.id); open = false })
            }
        }
    }
}
