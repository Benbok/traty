package ru.my.traty.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.reconcile.ReconcileCard
import ru.my.traty.ui.reconcile.ReconcilePromptViewModel
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.PendingStatus
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.capture.MicButton
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.budget.PlanSummaryCard
import ru.my.traty.ui.weekly.WeeklyTasksHomeCard
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.income.DeleteIncomeConfirmDialog
import ru.my.traty.ui.income.IncomeDetailsDialog
import ru.my.traty.ui.income.IncomeViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Главный экран списка трат (HomeScreen).
 * Поддерживает просмотр трат за расчётный период, выбор конкретной даты в календаре,
 * фильтрацию по дню и быстрый ввод трат текстом/голосом.
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenReconcile: () -> Unit,
    prompt: ReconcilePromptViewModel = viewModel(),
    onOpenBudget: () -> Unit = {},
    startRecording: Boolean = false,
    onStartRecordingHandled: () -> Unit = {},
    onOpenWeekly: () -> Unit = {},
    incomeVm: IncomeViewModel = viewModel(),
    searchVm: FeedSearchViewModel = viewModel(),
) {
    val showReconcileCard by prompt.showCard.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { prompt.refresh() }
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val allIncomes by vm.incomes.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val totalLimit by vm.totalLimit.collectAsStateWithLifecycle()
    val observations by vm.observations.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }
    var incomeOpened by remember { mutableStateOf<Income?>(null) }
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }

    // «Назад» сначала закрывает поиск, потом сбрасывает выбранный день, и только потом выходит из приложения
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }
    BackHandler(enabled = !searchActive && selectedDate != null) { vm.clearSelectedDate() }

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    val displayedExpenses = remember(expenses, selectedDate, searchQuery) {
        val base = if (selectedDate == null) {
            expenses
        } else {
            expenses.filter { e ->
                val expenseDate = Instant.ofEpochMilli(e.expense.timestamp)
                    .atZone(zone)
                    .toLocalDate()
                expenseDate == selectedDate
            }
        }
        base.filter { FeedSearch.matches(it, searchQuery) }
    }

    // Фильтрация доходов по дате и поиску
    val displayedIncomes = remember(allIncomes, selectedDate, searchQuery) {
        val base = if (selectedDate == null) allIncomes
        else allIncomes.filter { i ->
            Instant.ofEpochMilli(i.timestamp).atZone(zone).toLocalDate() == selectedDate
        }
        base.filter { FeedSearch.matches(it, searchQuery) }
    }

    // Поиск в остальных периодах: «холодильник» мог быть куплен давно
    LaunchedEffect(searchQuery, period) { searchVm.update(searchQuery, period) }
    val elsewhere by searchVm.elsewhere.collectAsStateWithLifecycle()

    // Единая лента: траты + доходы, отсортированная по времени
    val labels by vm.labels.collectAsStateWithLifecycle()
    val feed = remember(displayedExpenses, displayedIncomes) {
        buildFeed(displayedExpenses, displayedIncomes)
    }
    val totalExpense = remember(displayedExpenses) { displayedExpenses.sumOf { it.expense.amountKopecks } }
    val totalIncome = remember(displayedIncomes) { displayedIncomes.sumOf { it.amountKopecks } }

    val mic = rememberHomeMic(vm)
    // «Записать первую трату» из знакомства: включить микрофон, как только открылась лента
    LaunchedEffect(startRecording) {
        if (startRecording) {
            mic.start()
            onStartRecordingHandled()
        }
    }
    val pending by vm.pending.collectAsStateWithLifecycle()
    val waitingCount = remember(pending) { pending.count { it.status == PendingStatus.WAITING } }
    val parsedPending = remember(pending) { pending.filter { it.status == PendingStatus.PARSED } }

    Column(Modifier.fillMaxSize().imePadding()) {
        TratyHeaderCard(
            title = "Траты",
            subtitle = if (selectedDate != null) "День: ${formatDate(selectedDate!!)}" else "Учёт расходов и доходов",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            actions = {
                TratyIconButton(
                    icon = Icons.Filled.Search,
                    contentDescription = "Поиск",
                    onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                    containerColor = if (searchActive) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                    tint = if (searchActive) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            },
            content = if (searchActive) {
                { FeedSearchField(query = searchQuery, onQueryChange = { searchQuery = it }) }
            } else null,
        )

        TratyMonthPicker(
            periodLabel = formatPeriod(period, settings.periodStartDay),
            totalKopecks = totalExpense,
            selectedDate = selectedDate,
            onPrev = vm::prevPeriod,
            onNext = vm::nextPeriod,
            onDateClick = { showDatePicker = true },
            onClearDate = { vm.clearSelectedDate() },
        )

        // Чип активного фильтра по конкретной дате
        if (selectedDate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InputChip(
                    selected = true,
                    onClick = { showDatePicker = true },
                    label = { Text("Дата: ${formatDate(selectedDate!!)}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Сбросить фильтр",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                        selectedLeadingIconColor = TratyTheme.colors.primary,
                        selectedTrailingIconColor = TratyTheme.colors.primary,
                    ),
                    shape = TratyTheme.shapes.small,
                )

                TratyTextButton(
                    onClick = vm::clearSelectedDate,
                ) {
                    Text("Весь период", style = TratyTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (showReconcileCard) {
                item(key = "reconcile-card", contentType = "reconcile-card") {
                    ReconcileCard(onReconcile = onOpenReconcile, onSnooze = prompt::snooze)
                }
            }
            if (waitingCount > 0) {
                item(key = "pending-waiting", contentType = "pending") {
                    PendingCard(title = pendingWaitingText(waitingCount), subtitle = "Разоберу, когда появится интернет")
                }
            }
            items(parsedPending, key = { "pending-${it.id}" }, contentType = { "pending" }) { p ->
                PendingCard(
                    title = "Разобрано, проверьте",
                    subtitle = "«${p.text}»",
                    onClick = { vm.openParsedPending(p) },
                )
            }
            // Наблюдения показываем только в текущем периоде и на всём периоде, не на выбранном дне
            if (selectedDate == null && LocalDate.now() in period) {
                items(observations, key = { "observation-${it.key}" }, contentType = { "observation" }) { o ->
                    ObservationCard(
                        o,
                        onDismiss = { vm.dismissObservation(o.key) },
                        explanation = vm.observationExplanations[o.key],
                        explaining = vm.explainingObservation == o.key,
                        onExplain = { vm.explainObservation(o) },
                    )
                }
            }
            // Шапка «потрачено / пополнено / баланс»
            if (totalIncome > 0 || totalExpense > 0) {
                item(key = "balance-header", contentType = "balance-header") {
                    BalanceSummaryRow(totalExpense = totalExpense, totalIncome = totalIncome)
                }
            }
            // План трат — за весь период, поэтому при фильтре по дню или поиске не показываем
            if ((plans.isNotEmpty() || totalLimit != null) && selectedDate == null && searchQuery.isBlank()) {
                item(key = "plan", contentType = "plan") {
                    PlanSummaryCard(
                        items = plans,
                        total = totalLimit,
                        period = period,
                        onOpen = onOpenBudget,
                        compact = true,
                        detailsExpanded = settings.homePlanExpanded,
                        onToggleDetails = { vm.setHomePlanExpanded(!settings.homePlanExpanded) },
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                }
            }
            if (selectedDate == null && searchQuery.isBlank()) {
                item(key = "weekly", contentType = "weekly") {
                    WeeklyTasksHomeCard(
                        onOpen = onOpenWeekly,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                }
            }
            if (feed.isEmpty() && elsewhere.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    FeedEmptyState(selectedDate = selectedDate, searchQuery = searchQuery, onShowAll = vm::clearSelectedDate)
                }
            }
            // contentType: при прокрутке LazyColumn переиспользует уже собранные строки того же типа
            // (трата ↔ трата), а не строит каждую с нуля — это и убирает рывки на длинном списке.
            items(feed, key = { it.id }, contentType = { it.contentType }) { item ->
                when (item) {
                    is FeedItem.ExpenseItem -> {
                        SwipeableExpenseRow(
                            item = item.data,
                            labels = labels[item.data.expense.id].orEmpty(),
                            onClick = { vm.editing = item.data },
                            onDelete = { expenseToDelete = item.data },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    is FeedItem.IncomeItem -> {
                        SwipeableIncomeRow(
                            income = item.data,
                            onClick = { incomeOpened = item.data },
                            onDelete = { incomeToDelete = item.data },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                HorizontalDivider(
                    color = TratyTheme.colors.borderSubtle,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
            }
            searchElsewhere(
                groups = elsewhere,
                startDay = settings.periodStartDay,
                currentEmpty = feed.isEmpty(),
                onExpenseClick = { vm.editing = it },
                onIncomeClick = { incomeOpened = it },
            )
        }

        MicButton(
            state = mic.state,
            busy = vm.busy,
            compact = true,
            onClick = mic::onClick,
        )
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = selectedDate ?: LocalDate.now(),
            onDateSelected = { pickedDate ->
                vm.selectDate(pickedDate)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    incomeOpened?.let { inc ->
        IncomeDetailsDialog(
            income = inc,
            onDelete = {
                incomeVm.delete(inc)
                incomeOpened = null
            },
            onDismiss = { incomeOpened = null },
        )
    }

    incomeToDelete?.let { inc ->
        DeleteIncomeConfirmDialog(
            income = inc,
            onConfirm = {
                incomeVm.delete(inc)
                incomeToDelete = null
            },
            onDismiss = { incomeToDelete = null },
        )
    }

    expenseToDelete?.let { item ->
        DeleteExpenseConfirmDialog(
            item = item,
            onConfirm = {
                vm.delete(item.expense)
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null },
        )
    }
}
