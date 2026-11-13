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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.LlmProvider
import ru.my.traty.speech.SystemSpeech
import java.time.YearMonth
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
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
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
    val categories by vm.categories.collectAsStateWithLifecycle()
    val emojiOf = remember(categories) { categories.associate { it.name to it.emoji } }
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                vm.input = text
                vm.parseInput() // сразу разбираем — подтверждение всё равно будет
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        MonthHeader(month, expenses.sumOf { it.amountKopecks }, vm::prevMonth, vm::nextMonth)
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (expenses.isEmpty()) {
                item {
                    Text(
                        "Трат пока нет.\nСкажите или напишите, например: «кофе 250 и такси 400».",
                        Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(expenses, key = { it.id }) { e ->
                ExpenseRow(e, emojiOf[e.category] ?: "📦") { toDelete = e }
                HorizontalDivider()
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::manualDraft) { Icon(Icons.Filled.Add, "Вручную") }
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("кофе 250, такси 400…") },
                    maxLines = 3,
                )
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                } else if (vm.input.isBlank()) {
                    IconButton(onClick = {
                        try { speech.launch(SystemSpeech.intent()) } catch (e: ActivityNotFoundException) {
                            vm.message = "На телефоне нет сервиса распознавания речи"
                        }
                    }) { Icon(Icons.Filled.Mic, "Голос") }
                } else {
                    IconButton(onClick = vm::parseInput) { Icon(Icons.AutoMirrored.Filled.Send, "Разобрать") }
                }
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить трату?") },
            text = { Text("${e.category}: ${formatRub(e.amountKopecks)}\n${e.note ?: e.rawText ?: ""}") },
            confirmButton = { TextButton(onClick = { vm.delete(e); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, totalKopecks: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatMonth(month), style = MaterialTheme.typography.titleMedium)
            Text(formatRub(totalKopecks), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") }
    }
}

@Composable
private fun ExpenseRow(e: Expense, emoji: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = listOfNotNull(e.subcategory ?: e.category, e.place).joinToString(" · ")
            Text(title, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(formatTime(e.timestamp), e.note).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatRub(e.amountKopecks), style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------- Подтверждение

@Composable
private fun ConfirmDialog(
    draft: Draft,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
) {
    var items by remember(draft) { mutableStateOf(draft.items) }
    fun update(i: Int, f: (DraftItem) -> DraftItem) {
        items = items.toMutableList().also { it[i] = f(it[i]) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.size > 1) "Проверьте траты (${items.size})" else "Проверьте трату") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (draft.rawText.isNotBlank()) {
                    Text("«${draft.rawText}»", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                items.forEachIndexed { i, item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { v -> update(i) { d -> d.copy(amountText = v) } },
                                    label = { Text("Сумма, ₽") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                if (items.size > 1) {
                                    IconButton(onClick = { items = items.filterIndexed { j, _ -> j != i } }) {
                                        Icon(Icons.Filled.Close, "Убрать")
                                    }
                                }
                            }
                            CategoryPicker(item.category, categories, item.suggestedCategory) { name ->
                                update(i) { d -> d.copy(category = name) }
                            }
                            OutlinedTextField(
                                item.subcategory, { v -> update(i) { d -> d.copy(subcategory = v) } },
                                label = { Text("Подкатегория") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                item.place, { v -> update(i) { d -> d.copy(place = v) } },
                                label = { Text("Место") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                item.note, { v -> update(i) { d -> d.copy(note = v) } },
                                label = { Text("Заметка") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                item.dateText, { v -> update(i) { d -> d.copy(dateText = v) } },
                                label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft.copy(items = items)) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CategoryPicker(
    selected: String,
    categories: List<Category>,
    suggested: Category?,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Предложение модели показываем, только если такой категории ещё нет в базе
    val newOne = suggested?.takeIf { s -> categories.none { it.name.equals(s.name, ignoreCase = true) } }
    val isNew = newOne != null && selected == newOne.name
    val emoji = categories.firstOrNull { it.name == selected }?.emoji ?: newOne?.takeIf { isNew }?.emoji ?: ""
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
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
    val categories by vm.categories.collectAsStateWithLifecycle()
    val emojiOf = remember(categories) { categories.associate { it.name to it.emoji } }
    val prevMap = remember(prev) { prev.associate { it.category to it.total } }
    val sum = totals.sumOf { it.total }

    LazyColumn(Modifier.fillMaxSize()) {
        item { MonthHeader(month, sum, vm::prevMonth, vm::nextMonth) }
        item {
            val prevSum = prev.sumOf { it.total }
            if (prevSum > 0) {
                Text(
                    "Прошлый месяц: ${formatRub(prevSum)} (${deltaText(sum, prevSum)})",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
        items(totals, key = { it.category }) { t ->
            val frac = if (sum > 0) t.total.toFloat() / sum else 0f
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${emojiOf[t.category] ?: "📦"} ${t.category}", Modifier.weight(1f))
                    Text(formatRub(t.total), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                val p = prevMap[t.category]
                Text(
                    "${(frac * 100).roundToInt()}% от всех трат" + (p?.let { " · к прошлому месяцу ${deltaText(t.total, it)}" } ?: " · новая категория"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Комментарий ИИ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Отправляются только суммы по категориям — без мест и заметок.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    vm.insight?.let { Text(it); Spacer(Modifier.height(8.dp)) }
                    if (vm.insightBusy) CircularProgressIndicator()
                    else Button(onClick = vm::requestInsight) { Text(if (vm.insight == null) "Получить" else "Обновить") }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Разбор фраз", style = MaterialTheme.typography.titleMedium)
        LlmProvider.entries.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { s = s.copy(provider = p) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.provider == p, onClick = { s = s.copy(provider = p) })
                Text(p.title)
            }
        }

        HorizontalDivider()
        Text("DeepSeek", style = MaterialTheme.typography.titleMedium)
        SecretField("API-ключ DeepSeek", s.deepseekKey) { s = s.copy(deepseekKey = it) }
        OutlinedTextField(
            s.deepseekModel, { s = s.copy(deepseekModel = it) },
            label = { Text("Модель") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("YandexGPT", style = MaterialTheme.typography.titleMedium)
        SecretField("API-ключ Yandex Cloud", s.yandexKey) { s = s.copy(yandexKey = it) }
        OutlinedTextField(
            s.yandexFolderId, { s = s.copy(yandexFolderId = it) },
            label = { Text("ID каталога (folder id)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            s.yandexModel, { s = s.copy(yandexModel = it) },
            label = { Text("Модель (напр. yandexgpt-lite/latest)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveSettings(s) }) { Text("Сохранить") }
            OutlinedButton(onClick = { vm.testConnection(s) }) { Text("Проверить") }
        }

        HorizontalDivider()
        Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
        Text(
            "Все данные хранятся только на телефоне. Периодически выгружайте CSV (открывается в Excel).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { exportLauncher.launch("traty-${java.time.LocalDate.now()}.csv") }) {
            Text("Экспорт в CSV")
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
    )
}
