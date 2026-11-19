package ru.my.traty.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.reconcile.ReconcileCard
import ru.my.traty.ui.reconcile.ReconcilePromptViewModel
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.isUserEditable
import ru.my.traty.data.Income
import ru.my.traty.data.PendingStatus
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.rememberKeyboardOpen
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.budget.PlanSummaryCard
import ru.my.traty.ui.weekly.WeeklyTasksHomeCard
import ru.my.traty.ui.income.IncomeViewModel
import ru.my.traty.ui.purchases.PurchaseForm
import ru.my.traty.ui.purchases.PurchaseGoalForms
import ru.my.traty.ui.purchases.PurchaseGoalsHomeBlock
import ru.my.traty.ui.purchases.PurchaseGoalViewModel
import ru.my.traty.ui.purchases.activeGoals
import ru.my.traty.ui.purchases.purchaseGoalToday
import ru.my.traty.ui.update.UpdateBanner
import ru.my.traty.ui.update.UpdateViewModel
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
    updateVm: UpdateViewModel = viewModel(),
    itemsVm: FeedItemsViewModel = viewModel(),
    purchasesVm: PurchaseGoalViewModel = viewModel(),
    onOpenPurchases: () -> Unit = {},
    revealPurchaseGoals: Boolean = false,
    onPurchaseGoalsRevealed: () -> Unit = {},
) {
    // Сколько товаров в покупках: в строке ленты «5 товаров» вместо длинного списка в заметке
    val itemCounts by itemsVm.counts.collectAsStateWithLifecycle()
    val update by updateVm.available.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
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

    // Товары покупок: пока в поиске есть текст, ищем и по ним, и показываем найденные под строкой
    val searchItems by searchVm.items.collectAsStateWithLifecycle()
    val displayedExpenses = remember(expenses, selectedDate, searchQuery, searchItems) {
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
        base.filter { FeedSearch.matches(it, searchQuery, searchItems[it.expense.id].orEmpty()) }
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
    // Заголовки дней нужны в общей ленте; внутри одного выбранного дня они только повторяли бы дату
    val dayHeaders = remember(feed, selectedDate) { if (selectedDate == null) feed.dayHeaders(zone) else emptyMap() }
    val today by purchaseGoalToday()
    val transfers by purchasesVm.cancelledTransfers.collectAsStateWithLifecycle()
    val totalExpense = remember(displayedExpenses, transfers) { transfers.expenseTotal(displayedExpenses) }
    val totalIncome = remember(displayedIncomes, transfers) { transfers.incomeTotal(displayedIncomes) }

    val mic = rememberHomeMic(vm)
    // Голосовой ввод запроса в поиск (без ИИ): текст попадает в строку поиска
    val searchMic = rememberSearchMic(settings = { settings }, onQuery = { searchQuery = it }, onError = { vm.message = it })
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

    val purchaseGoals by purchasesVm.goals.collectAsStateWithLifecycle()
    val purchases = purchaseGoals.orEmpty()
    val activePurchases = remember(purchases, today) { purchases.activeGoals(today) }
    val editableContributions = remember(activePurchases) {
        activePurchases.flatMap { it.contributions }.mapTo(mutableSetOf()) { it.id }
    }
    var purchaseForm by remember { mutableStateOf<PurchaseForm?>(null) }
    var purchasesExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val openPurchaseForm: (PurchaseForm) -> Unit = {
        purchasesVm.clearError()
        purchaseForm = it
    }
    val normalFeed = selectedDate == null && searchQuery.isBlank()
    val showObservations = selectedDate == null && today in period
    val showBalance = totalIncome > 0L || totalExpense > 0L
    val showPlan = (plans.isNotEmpty() || totalLimit != null) && normalFeed
    val showPurchaseGoals = normalFeed && !searchActive && today in period && purchases.isNotEmpty()
    val purchaseItemIndex = parsedPending.size + listOf(
        update != null, showReconcileCard, waitingCount > 0, showBalance, showPlan, normalFeed,
    ).count { it } + if (showObservations) observations.size else 0

    LaunchedEffect(revealPurchaseGoals) {
        if (revealPurchaseGoals) {
            searchActive = false
            searchQuery = ""
            vm.selectDate(today)
            vm.clearSelectedDate()
            purchasesExpanded = true
        }
    }
    LaunchedEffect(revealPurchaseGoals, showPurchaseGoals, purchaseGoals != null, purchaseItemIndex) {
        if (revealPurchaseGoals && showPurchaseGoals) {
            listState.scrollToItem(purchaseItemIndex)
            onPurchaseGoalsRevealed()
        } else if (revealPurchaseGoals && purchaseGoals != null && activePurchases.isEmpty()) {
            onPurchaseGoalsRevealed()
        }
    }
    val openExpense: (ExpenseWithCategory) -> Unit = { item ->
        when {
            item.expense.id in editableContributions ->
                openPurchaseForm(PurchaseForm.EditContribution(item.expense.id))
            item.expense.source == ExpenseSource.PURCHASE_CONTRIBUTION ||
                item.expense.source == ExpenseSource.PURCHASE_SETTLEMENT -> onOpenPurchases()
            else -> vm.editing = item
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        HomeBrowseHeader(
            selectedDate = selectedDate,
            period = period,
            periodStartDay = settings.periodStartDay,
            totalExpense = totalExpense,
            searchActive = searchActive,
            onToggleSearch = {
                searchActive = !searchActive
                if (!searchActive) searchQuery = ""
            },
            searchContent = {
                FeedSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    mic = searchMic,
                    micEnabled = mic.state is RecordState.Idle && !vm.busy,
                )
            },
            onPrevious = vm::prevPeriod,
            onNext = vm::nextPeriod,
            onPickDate = { showDatePicker = true },
            onClearDate = vm::clearSelectedDate,
            onCreatePurchase = { openPurchaseForm(PurchaseForm.Create) },
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            update?.let { u ->
                item(key = "update-banner", contentType = "update-banner") {
                    UpdateBanner(
                        version = u.version,
                        installedVersion = updateVm.installedVersion,
                        onDownload = { uriHandler.openUri(u.url) },
                        onDismiss = { updateVm.dismiss(u.version) },
                    )
                }
            }
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
            if (showObservations) {
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
            if (showBalance) {
                item(key = "balance-header", contentType = "balance-header") {
                    BalanceSummaryRow(totalExpense = totalExpense, totalIncome = totalIncome)
                }
            }
            // План трат — за весь период, поэтому при фильтре по дню или поиске не показываем
            if (showPlan) {
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
            if (normalFeed) {
                item(key = "weekly", contentType = "weekly") {
                    WeeklyTasksHomeCard(
                        onOpen = onOpenWeekly,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                }
            }
            if (showPurchaseGoals) {
                item(key = "purchase-goals", contentType = "purchase-goals") {
                    PurchaseGoalsHomeBlock(
                        goals = activePurchases,
                        today = today,
                        expanded = purchasesExpanded,
                        onToggle = { purchasesExpanded = !purchasesExpanded },
                        onOpen = onOpenPurchases,
                        onAction = openPurchaseForm,
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
                dayHeaders[item.id]?.let { FeedDayHeader(it, today) }
                when (item) {
                    is FeedItem.ExpenseItem -> {
                        SwipeableExpenseRow(
                            item = item.data,
                            labels = labels[item.data.expense.id].orEmpty(),
                            matchedItems = FeedSearch.matchedItems(searchItems[item.data.expense.id].orEmpty(), searchQuery),
                            itemCount = itemCounts[item.data.expense.id] ?: 0,
                            onClick = { openExpense(item.data) },
                            canDelete = item.data.expense.isUserEditable || item.data.expense.id in editableContributions,
                            onDelete = {
                                val expenseId = item.data.expense.id
                                if (expenseId in editableContributions) {
                                    openPurchaseForm(PurchaseForm.DeleteContribution(expenseId))
                                } else {
                                    expenseToDelete = item.data
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    is FeedItem.IncomeItem -> {
                        SwipeableIncomeRow(
                            income = item.data,
                            internalTransfer = transfers.isRefund(item.data),
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
                onExpenseClick = openExpense,
                onIncomeClick = { incomeOpened = it },
                itemsByExpense = searchItems,
                query = searchQuery,
                itemCounts = itemCounts,
            )
        }

        // Пока на экране клавиатура (поиск, ввод текста), вторую кнопку записи не показываем: микрофон есть в поле ввода.
        // Если запись или разбор уже идут, кнопку оставляем, чтобы её можно было остановить.
        val keyboardOpen by rememberKeyboardOpen()
        if (!keyboardOpen || mic.state !is RecordState.Idle || vm.busy) {
            HomeCaptureBar(mic = mic, busy = vm.busy, onError = { vm.message = it })
        }
    }

    HomeFeedDialogs(
        pickerDate = if (showDatePicker) selectedDate ?: today else null,
        onPickDate = {
            vm.selectDate(it)
            showDatePicker = false
        },
        onDismissPicker = { showDatePicker = false },
        openedIncome = incomeOpened,
        onDismissIncome = { incomeOpened = null },
        incomeToDelete = incomeToDelete,
        onDismissIncomeDelete = { incomeToDelete = null },
        onDeleteIncome = { incomeVm.delete(it) },
        expenseToDelete = expenseToDelete,
        onDismissExpenseDelete = { expenseToDelete = null },
        onDeleteExpense = { vm.delete(it) },
    )

    purchaseForm?.let { form ->
        PurchaseGoalForms(form, purchases, today, purchasesVm, onDismiss = { purchaseForm = null })
    }
}
