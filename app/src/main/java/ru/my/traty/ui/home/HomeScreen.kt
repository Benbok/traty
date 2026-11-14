package ru.my.traty.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.reconcile.ReconcileCard
import ru.my.traty.ui.reconcile.ReconcilePromptViewModel
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.PendingStatus
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.capture.MicButton
import ru.my.traty.ui.capture.ChimeRecordCues
import ru.my.traty.ui.capture.RecordController
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.budget.PlanSummaryCard
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
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
) {
    val showReconcileCard by prompt.showCard.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { prompt.refresh() }
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val allIncomes by vm.incomes.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val observations by vm.observations.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }

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
        if (searchQuery.isBlank()) base
        else {
            val q = searchQuery.trim().lowercase()
            base.filter { e ->
                e.categoryName.lowercase().contains(q)
                    || e.subcategoryName?.lowercase()?.contains(q) == true
                    || e.expense.place?.lowercase()?.contains(q) == true
                    || e.expense.note?.lowercase()?.contains(q) == true
                    || e.expense.rawText?.lowercase()?.contains(q) == true
                    || formatRub(e.expense.amountKopecks).contains(q)
            }
        }
    }

    // Фильтрация доходов по дате и поиску
    val displayedIncomes = remember(allIncomes, selectedDate, searchQuery) {
        val base = if (selectedDate == null) allIncomes
        else allIncomes.filter { i ->
            Instant.ofEpochMilli(i.timestamp).atZone(zone).toLocalDate() == selectedDate
        }
        if (searchQuery.isBlank()) base
        else {
            val q = searchQuery.trim().lowercase()
            base.filter { i ->
                i.source.lowercase().contains(q)
                    || i.note?.lowercase()?.contains(q) == true
                    || formatRub(i.amountKopecks).contains(q)
            }
        }
    }

    // Единая лента: траты + доходы, отсортированная по времени
    val labels by vm.labels.collectAsStateWithLifecycle()
    val feed = remember(displayedExpenses, displayedIncomes) {
        buildFeed(displayedExpenses, displayedIncomes)
    }
    val totalExpense = remember(displayedExpenses) { displayedExpenses.sumOf { it.expense.amountKopecks } }
    val totalIncome = remember(displayedIncomes) { displayedIncomes.sumOf { it.amountKopecks } }

    val context = LocalContext.current
    val recorder = remember { RecordController({ SpeechEngines.create(context, vm.settings.value) }, ChimeRecordCues()) { vm.onSpokenText(it) } }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    val recordState by recorder.state.collectAsStateWithLifecycle()
    // Под компактной кнопкой нет текста, поэтому ошибку записи показываем всплывающим сообщением
    LaunchedEffect(recordState) {
        (recordState as? RecordState.Error)?.let { vm.message = it.message }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.toggle() else vm.message = "Нужен доступ к микрофону"
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
                {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Поиск по тратам…",
                                style = TratyTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Очистить",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = TratyTheme.shapes.small,
                        textStyle = TratyTheme.typography.bodyMedium,
                    )
                }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Расходы", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                            Text(formatRub(totalExpense), style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.expense)
                        }
                        if (totalIncome > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Доходы", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                                Text("+${formatRub(totalIncome)}", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.income)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Баланс", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                                val balance = totalIncome - totalExpense
                                val balColor = if (balance >= 0) TratyTheme.colors.income else TratyTheme.colors.expense
                                Text(
                                    (if (balance >= 0) "+" else "") + formatRub(kotlin.math.abs(balance)),
                                    style = TratyTheme.typography.titleSmall,
                                    color = balColor,
                                )
                            }
                        }
                    }
                }
            }
            // План трат — за весь период, поэтому при фильтре по дню или поиске не показываем
            if (plans.isNotEmpty() && selectedDate == null && searchQuery.isBlank()) {
                item(key = "plan", contentType = "plan") {
                    PlanSummaryCard(
                        items = plans,
                        period = period,
                        onOpen = onOpenBudget,
                        compact = true,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                }
            }
            if (feed.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    if (selectedDate != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = TratyTheme.spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TratyEmptyState(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "За ${formatDate(selectedDate!!)} записей нет",
                                description = "Нажмите «Показать весь период», чтобы увидеть все записи периода.",
                            )
                            Spacer(Modifier.height(TratyTheme.spacing.md))
                            TratyButton(onClick = vm::clearSelectedDate) {
                                Text("Показать весь период")
                            }
                        }
                    } else {
                        TratyEmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "Трат пока нет",
                            description = "Нажмите микрофон внизу и скажите, например: «кофе 250 и такси 400».",
                        )
                    }
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
                        IncomeRow(
                            income = item.data,
                            onClick = { /* TODO: редактирование дохода */ },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                HorizontalDivider(
                    color = TratyTheme.colors.borderSubtle,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
            }
        }

        MicButton(
            state = recordState,
            busy = vm.busy,
            compact = true,
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (recordState is RecordState.Listening || granted) recorder.toggle()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
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
