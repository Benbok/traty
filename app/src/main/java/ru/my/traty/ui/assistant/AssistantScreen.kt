package ru.my.traty.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import ru.my.traty.assistant.PlanProposal
import ru.my.traty.assistant.DebtChange
import ru.my.traty.assistant.ExpenseChange
import ru.my.traty.assistant.SubscriptionChange
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.capture.ChimeRecordCues
import ru.my.traty.ui.capture.RecordController
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.ExpenseRow
import ru.my.traty.ui.stats.ModeChip

/** Готовые вопросы на пустом экране: чтобы не думать, что спросить. */
private val SUGGESTIONS = listOf(
    "Сколько я потратил в этом месяце?",
    "На что ушло больше всего?",
    "Сравни с прошлым месяцем",
    "Как менялись траты по месяцам?",
    "Когда я последний раз был в кафе?",
    "Топ-5 покупок за месяц",
    "Какие у меня подписки?",
    "Могу ли я потратить 10 000 ₽ до конца месяца?",
    "Как я иду по плану трат?",
    "Предложи план трат на месяц",
    "Сколько у меня денег на счёте?",
    "Где я могу сэкономить?",
)

/** Вкладка «ИИ»: вопросы о тратах текстом или голосом, ответы по данным на телефоне. */
@Composable
fun AssistantScreen(
    vm: AssistantViewModel = viewModel(),
    onOpenBudget: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenDebts: () -> Unit = {},
    onOpenExpense: (ExpenseWithCategory) -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }

    val recorder = remember { RecordController({ SpeechEngines.create(context, vm.settings.value) }, ChimeRecordCues()) { vm.ask(it) } }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    val recordState by recorder.state.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.toggle()
    }
    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (recordState is RecordState.Listening || granted) recorder.toggle() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val send = {
        vm.ask(input)
        input = ""
    }

    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.busy) {
        val last = vm.messages.size + (if (vm.busy) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TratyHeaderCard(
            title = "ИИ-помощник",
            subtitle = "Спросите о тратах, доходах и подписках",
            icon = Icons.Filled.AutoAwesome,
            actions = {
                if (vm.messages.isNotEmpty()) {
                    TratyIconButton(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = "Начать заново",
                        onClick = vm::clear,
                        tint = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            if (vm.messages.isEmpty()) {
                item(key = "intro") {
                    Intro(hasAi = vm.hasAi(settings), onPick = vm::ask)
                }
            }
            items(vm.messages, key = { it.id }) { m ->
                if (m.fromUser) UserBubble(m.text) else AssistantBubble(
                    m,
                    onShowExpenses = { vm.showExpenses(m.expenseIds) },
                    onShowItemSuggestion = { vm.showItemSuggestion(m.id, it) },
                    onApplyPlan = { vm.applyPlan(m.id, it) },
                    onApplySubscriptions = { vm.applySubscriptions(m.id, it) },
                    onApplyDebts = { vm.applyDebts(m.id, it) },
                    onApplyExpenses = { vm.applyExpenses(m.id, it) },
                    onUndoExpenses = { vm.undoExpenses(m.id) },
                    onOpenBudget = onOpenBudget,
                    onOpenRecurring = onOpenRecurring,
                    onOpenDebts = onOpenDebts,
                )
            }
            if (vm.busy) {
                item(key = "busy") { Thinking() }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            recordState = recordState,
            busy = vm.busy,
            onSend = send,
            onMic = onMic,
        )
    }

    vm.sheetRows?.let { rows ->
        ExpensesSheet(
            rows,
            onOpenExpense = {
                vm.closeSheet()
                onOpenExpense(it)
            },
            onDismiss = vm::closeSheet,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(hasAi: Boolean, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md)) {
        Text(
            "Цифры считает приложение по вашим записям на телефоне. В ИИ уходят только итоговые суммы, названия категорий и мест, " +
                "а он объясняет их и подсказывает, что можно сделать.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        if (!hasAi) {
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            Text(
                "Сейчас выбран разбор без ИИ: простые вопросы я понимаю по ключевым словам. Для свободных формулировок подключите DeepSeek или YandexGPT в Настройках → «Разбор фраз и ИИ».",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.warning,
            )
        }
        Spacer(Modifier.height(TratyTheme.spacing.md))
        Text("Можно спросить", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(TratyTheme.spacing.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            SUGGESTIONS.forEach { s -> ModeChip(s, selected = false, onClick = { onPick(s) }) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onPrimaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(TratyTheme.colors.primaryContainer, TratyTheme.shapes.medium)
                .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
        )
    }
}

@Composable
private fun AssistantBubble(
    m: ChatMessage,
    onShowExpenses: () -> Unit,
    onShowItemSuggestion: (Int) -> Unit,
    onApplyPlan: (List<PlanProposal>) -> Unit,
    onApplySubscriptions: (List<SubscriptionChange>) -> Unit,
    onApplyDebts: (List<DebtChange>) -> Unit,
    onApplyExpenses: (List<ExpenseChange>) -> Unit,
    onUndoExpenses: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDebts: () -> Unit,
) {
    var showFacts by rememberSaveable(m.id) { mutableStateOf(false) }
    TratyCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg),
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            if (m.headline != null) {
                Text(m.headline, style = TratyTheme.typography.mediumAmount, color = TratyTheme.colors.onSurface)
                m.caption?.let {
                    Text(it, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(TratyTheme.spacing.sm))
            }
            Text(
                m.text,
                style = TratyTheme.typography.bodyMedium,
                color = if (m.error) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
            )
            if (m.planProposals.isNotEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                PlanProposals(m, onApply = onApplyPlan, onOpenBudget = onOpenBudget)
            }
            if (m.subscriptionChanges.isNotEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                SubscriptionChanges(m, onApply = onApplySubscriptions, onOpenRecurring = onOpenRecurring)
            }
            if (m.debtChanges.isNotEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                DebtChanges(m, onApply = onApplyDebts, onOpenDebts = onOpenDebts)
            }
            if (m.expenseChanges.isNotEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                ExpenseChanges(m, onApply = onApplyExpenses, onUndo = onUndoExpenses)
            }
            m.itemSuggestions.forEachIndexed { index, suggestion ->
                TratyTextButton(onClick = { onShowItemSuggestion(index) }) {
                    Text(suggestion.itemName ?: "Показать расчёт", style = TratyTheme.typography.labelMedium)
                }
            }
            if (m.expenseIds.isNotEmpty() || m.sentFacts != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    if (m.expenseIds.isNotEmpty()) {
                        TratyTextButton(onClick = onShowExpenses) {
                            Text("Показать траты (${m.expenseIds.size})", style = TratyTheme.typography.labelMedium)
                        }
                    }
                    if (m.sentFacts != null) {
                        TratyTextButton(onClick = { showFacts = !showFacts }, contentColor = TratyTheme.colors.onSurfaceVariant) {
                            Text(if (showFacts) "Скрыть" else "Что ушло в ИИ", style = TratyTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (showFacts && m.sentFacts != null) {
                HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                Text(
                    m.sentFacts,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = TratyTheme.spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(
        Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TratyTheme.colors.primary)
        Text("Считаю и думаю…", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    recordState: RecordState,
    busy: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val listening = recordState as? RecordState.Listening
    // Запись остановлена, идёт распознавание: микрофон не нажимается, чтобы не начать новую запись
    val recognizing = recordState is RecordState.Recognizing
    Column(Modifier.fillMaxWidth().background(TratyTheme.colors.surface)) {
        HorizontalDivider(color = TratyTheme.colors.borderSubtle)
        val hint = when {
            recognizing -> "Распознаю…"
            listening != null -> listening.partial.ifBlank { "Слушаю… нажмите ещё раз, когда закончите" }
            recordState is RecordState.Error -> recordState.message
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, top = TratyTheme.spacing.sm),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Спросите о тратах…") },
                modifier = Modifier.weight(1f),
                shape = TratyTheme.shapes.small,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!busy && value.isNotBlank()) onSend() }),
            )
            if (value.isBlank()) {
                TratyIconButton(
                    icon = if (listening != null) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (listening != null) "Закончить запись" else "Спросить голосом",
                    onClick = onMic,
                    enabled = !busy && !recognizing,
                    containerColor = TratyTheme.colors.primary,
                    tint = TratyTheme.colors.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                TratyIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    onClick = onSend,
                    enabled = !busy,
                    containerColor = TratyTheme.colors.primary,
                    tint = TratyTheme.colors.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensesSheet(rows: List<ExpenseWithCategory>, onOpenExpense: (ExpenseWithCategory) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg)) {
            Text("Траты из ответа", style = TratyTheme.typography.titleMedium)
            TratyAmountText(amountKopecks = rows.sumOf { it.expense.amountKopecks }, size = AmountSize.LARGE)
            Text("Записей: ${rows.size}", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(rows, key = { it.expense.id }) { r -> ExpenseRow(r, onClick = { onOpenExpense(r) }) }
            item { Spacer(Modifier.height(TratyTheme.spacing.lg)) }
        }
    }
}
