package ru.my.traty.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.LlmProvider
import ru.my.traty.speech.SystemSpeech
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyDeltaBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyInputBar
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import kotlin.math.roundToInt

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Траты", Icons.Filled.Home),
    STATS("Статистика", Icons.Filled.PieChart),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

@Composable
fun TratyRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val categories by vm.categories.collectAsStateWithLifecycle()

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = TratyTheme.colors.background,
        bottomBar = {
            NavigationBar(
                containerColor = TratyTheme.colors.surface,
                tonalElevation = 2.dp,
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title, style = TratyTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TratyTheme.colors.primary,
                            selectedTextColor = TratyTheme.colors.primary,
                            indicatorColor = TratyTheme.colors.primaryContainer,
                            unselectedIconColor = TratyTheme.colors.onSurfaceVariant,
                            unselectedTextColor = TratyTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.STATS -> StatsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }

    vm.draft?.let { d ->
        ConfirmDialog(d, categories, onDismiss = { vm.draft = null }, onSave = vm::saveDraft)
    }
}

// ---------------------------------------------------------------- Главный экран

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.onVoiceResult(text)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TratyMonthPicker(
            month = month,
            totalKopecks = expenses.sumOf { it.expense.amountKopecks },
            onPrev = vm::prevMonth,
            onNext = vm::nextMonth,
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (expenses.isEmpty()) {
                item {
                    TratyEmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Трат пока нет",
                        description = "Скажите или напишите, например: «кофе 250 и такси 400».",
                    )
                }
            }
            items(expenses, key = { it.expense.id }) { e ->
                ExpenseRow(e) { toDelete = e }
                HorizontalDivider(
                    color = TratyTheme.colors.borderSubtle,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
            }
        }

        TratyInputBar(
            value = vm.input,
            onValueChange = { vm.input = it },
            onManualClick = vm::manualDraft,
            onVoiceClick = {
                try {
                    speech.launch(SystemSpeech.intent())
                } catch (e: ActivityNotFoundException) {
                    vm.message = "На телефоне нет сервиса распознавания речи"
                }
            },
            onSendClick = vm::parseInput,
            isBusy = vm.busy,
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            shape = TratyTheme.shapes.large,
            containerColor = TratyTheme.colors.surface,
            title = { Text("Удалить трату?", style = TratyTheme.typography.titleMedium) },
            text = {
                Text(
                    "${e.categoryName}: ${formatRub(e.expense.amountKopecks)}\n${e.expense.note ?: e.expense.rawText ?: ""}",
                    style = TratyTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TratyTextButton(
                    onClick = { vm.delete(e.expense); toDelete = null },
                    contentColor = TratyTheme.colors.expense,
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TratyTextButton(onClick = { toDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ExpenseRow(row: ExpenseWithCategory, onClick: () -> Unit) {
    val e = row.expense
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(
            emoji = row.categoryEmoji,
            categoryName = row.categoryName,
            size = 42.dp,
        )
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            val title = listOfNotNull(row.subcategoryName ?: row.categoryName, e.place).joinToString(" · ")
            Text(
                text = title,
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.onSurface,
            )
            val sub = listOfNotNull(formatTime(e.timestamp), e.note).joinToString(" · ")
            Text(
                text = sub,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        TratyAmountText(
            amountKopecks = e.amountKopecks,
            size = AmountSize.MEDIUM,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------- Подтверждение

@Composable
private fun ConfirmDialog(
    draft: Draft,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
) {
    var items by remember(draft) { mutableStateOf(draft.items) }
    fun update(i: Int, f: (DraftItem) -> DraftItem) {
        items = items.toMutableList().also { it[i] = f(it[i]) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                if (items.size > 1) "Проверьте траты (${items.size})" else "Проверьте трату",
                style = TratyTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (draft.rawText.isNotBlank()) {
                    Text(
                        "«${draft.rawText}»",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                }
                items.forEachIndexed { i, item ->
                    TratyCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.xs),
                        backgroundColor = TratyTheme.colors.surfaceContainer,
                    ) {
                        Column(
                            Modifier.padding(TratyTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { v -> update(i) { d -> d.copy(amountText = v) } },
                                    label = { Text("Сумма, ₽") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = TratyTheme.shapes.small,
                                )
                                if (items.size > 1) {
                                    IconButton(onClick = { items = items.filterIndexed { j, _ -> j != i } }) {
                                        Icon(Icons.Filled.Close, "Убрать", tint = TratyTheme.colors.onSurfaceVariant)
                                    }
                                }
                            }
                            CategoryPicker(item.category, categories, item.suggestedCategory) { name ->
                                update(i) { d -> d.copy(category = name) }
                            }
                            OutlinedTextField(
                                value = item.subcategory,
                                onValueChange = { v -> update(i) { d -> d.copy(subcategory = v) } },
                                label = { Text("Подкатегория") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.place,
                                onValueChange = { v -> update(i) { d -> d.copy(place = v) } },
                                label = { Text("Место") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.note,
                                onValueChange = { v -> update(i) { d -> d.copy(note = v) } },
                                label = { Text("Заметка") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.dateText,
                                onValueChange = { v -> update(i) { d -> d.copy(dateText = v) } },
                                label = { Text("Дата (ГГГГ-ММ-ДД)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TratyButton(onClick = { onSave(draft.copy(items = items)) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun CategoryPicker(
    selected: String,
    categoryList: List<CategoryWithSubs>,
    suggested: NewCategory?,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val categories = categoryList.map { it.category }
    // Предложение модели показываем, только если такой категории ещё нет в базе
    val newOne = suggested?.takeIf { s -> categories.none { it.name.lowercase() == s.name.lowercase() } }
    val isNew = newOne != null && selected == newOne.name
    val emoji = categories.firstOrNull { it.name == selected }?.emoji ?: newOne?.takeIf { isNew }?.emoji ?: ""
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        ) {
            Text("$emoji $selected" + if (isNew) " (новая)" else "")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            newOne?.let { s ->
                DropdownMenuItem(
                    text = { Text("${s.emoji} ${s.name} (новая)") },
                    onClick = { onPick(s.name); open = false },
                )
                HorizontalDivider()
            }
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.emoji} ${c.name}") },
                    onClick = { onPick(c.name); open = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Статистика

@Composable
private fun StatsScreen(vm: MainViewModel) {
    val month by vm.month.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val prev by vm.prevTotals.collectAsStateWithLifecycle()
    val prevMap = remember(prev) { prev.associate { it.categoryId to it.total } }
    val sum = totals.sumOf { it.total }
    val prevSum = prev.sumOf { it.total }

    val deltaDescription = if (prevSum > 0) {
        "Прошлый месяц: ${formatRub(prevSum)} (${deltaText(sum, prevSum)})"
    } else null

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TratyMonthPicker(
                month = month,
                totalKopecks = sum,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                deltaText = deltaDescription,
            )
        }

        if (totals.isEmpty()) {
            item {
                TratyEmptyState(
                    icon = Icons.Filled.PieChart,
                    title = "Нет статистики",
                    description = "В этом месяце пока нет зарегистрированных трат.",
                )
            }
        }

        items(totals, key = { it.categoryId }) { t ->
            val frac = if (sum > 0) t.total.toFloat() / sum else 0f
            val p = prevMap[t.categoryId]

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TratyCategoryBadge(
                        emoji = t.emoji,
                        categoryName = t.name,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.md))
                    Text(
                        text = t.name,
                        style = TratyTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TratyAmountText(
                        amountKopecks = t.total,
                        size = AmountSize.MEDIUM,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                TratyProgressBar(
                    progress = frac,
                    modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${(frac * 100).roundToInt()}% от всех трат",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    if (p != null) {
                        TratyDeltaBadge(current = t.total, previous = p)
                    } else {
                        Text(
                            text = "новая категория",
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.info,
                        )
                    }
                }
            }
        }

        item {
            TratyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TratyTheme.spacing.lg),
                backgroundColor = TratyTheme.colors.surfaceContainer,
            ) {
                Column(Modifier.padding(TratyTheme.spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = TratyTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(TratyTheme.spacing.sm))
                        Text("Комментарий ИИ", style = TratyTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(TratyTheme.spacing.xs))

                    Text(
                        text = "Отправляются только суммы по категориям — без мест и заметок.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(TratyTheme.spacing.md))

                    vm.insight?.let {
                        Text(
                            text = it,
                            style = TratyTheme.typography.bodyMedium,
                            color = TratyTheme.colors.onSurface,
                        )
                        Spacer(Modifier.height(TratyTheme.spacing.md))
                    }

                    if (vm.insightBusy) {
                        CircularProgressIndicator(
                            color = TratyTheme.colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        TratyButton(onClick = vm::requestInsight) {
                            Text(if (vm.insight == null) "Получить анализ" else "Обновить анализ")
                        }
                    }
                }
            }
        }
    }
}

private fun deltaText(cur: Long, prev: Long): String {
    if (prev == 0L) return "—"
    val pct = ((cur - prev) * 100.0 / prev).roundToInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
}

// ---------------------------------------------------------------- Настройки

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var s by remember(saved) { mutableStateOf<AppSettings>(saved) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        // Секция: Провайдер ИИ
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Разбор фраз", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                LlmProvider.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { s = s.copy(provider = p) }
                            .padding(vertical = TratyTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.provider == p, onClick = { s = s.copy(provider = p) })
                        Spacer(Modifier.width(TratyTheme.spacing.sm))
                        Text(p.title, style = TratyTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Секция: DeepSeek
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("DeepSeek", style = TratyTheme.typography.titleMedium)
                SecretField("API-ключ DeepSeek", s.deepseekKey) { s = s.copy(deepseekKey = it) }
                OutlinedTextField(
                    value = s.deepseekModel,
                    onValueChange = { s = s.copy(deepseekModel = it) },
                    label = { Text("Модель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        }

        // Секция: YandexGPT
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("YandexGPT", style = TratyTheme.typography.titleMedium)
                SecretField("API-ключ Yandex Cloud", s.yandexKey) { s = s.copy(yandexKey = it) }
                OutlinedTextField(
                    value = s.yandexFolderId,
                    onValueChange = { s = s.copy(yandexFolderId = it) },
                    label = { Text("ID каталога (folder id)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                OutlinedTextField(
                    value = s.yandexModel,
                    onValueChange = { s = s.copy(yandexModel = it) },
                    label = { Text("Модель (напр. yandexgpt-lite/latest)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            TratyButton(onClick = { vm.saveSettings(s) }) { Text("Сохранить") }
            TratyOutlinedButton(onClick = { vm.testConnection(s) }) { Text("Проверить") }
        }

        // Секция: Резервная копия
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Резервная копия", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Все данные хранятся только на телефоне. Периодически выгружайте CSV (открывается в Excel).",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = { exportLauncher.launch("traty-${java.time.LocalDate.now()}.csv") }) {
                    Text("Экспорт в CSV")
                }
            }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
    )
}
