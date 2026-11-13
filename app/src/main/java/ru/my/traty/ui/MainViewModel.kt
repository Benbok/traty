package ru.my.traty.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.ai.ExpenseParsing
import ru.my.traty.ai.OfflineParser
import ru.my.traty.ai.ParsedExpense
import ru.my.traty.ai.clientFor
import ru.my.traty.data.AppSettings
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Expense
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

data class DraftItem(
    val amountText: String,
    val category: String,
    val subcategory: String,
    val place: String,
    val note: String,
    val dateText: String,
    /** Новая категория, предложенная моделью (её ещё нет в базе). Создаётся при сохранении, если выбрана. */
    val suggestedCategory: Category? = null,
)

data class Draft(val items: List<DraftItem>, val rawText: String)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    val settings: StateFlow<AppSettings> =
        c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val categories: StateFlow<List<Category>> =
        c.db.categories().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    val expenses: StateFlow<List<Expense>> = _month.flatMapLatest { m ->
        c.db.expenses().observeRange(m.startMillis(), m.plusMonths(1).startMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totals: StateFlow<List<CategoryTotal>> = _month.flatMapLatest { m ->
        c.db.expenses().observeTotals(m.startMillis(), m.plusMonths(1).startMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prevTotals: StateFlow<List<CategoryTotal>> = _month.flatMapLatest { m ->
        c.db.expenses().observeTotals(m.minusMonths(1).startMillis(), m.startMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Состояние экрана ---
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var draft by mutableStateOf<Draft?>(null)
    var message by mutableStateOf<String?>(null)
    var insight by mutableStateOf<String?>(null)
        private set
    var insightBusy by mutableStateOf(false)
        private set

    fun prevMonth() { _month.value = _month.value.minusMonths(1); insight = null }
    fun nextMonth() { _month.value = _month.value.plusMonths(1); insight = null }

    /** Текст (набранный или надиктованный) → черновик трат для подтверждения. */
    fun parseInput() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            val cats = c.db.categories().getAll()
            val client = clientFor(c.settings.current())
            val parsed: List<ParsedExpense> = if (client == null) {
                OfflineParser.parse(text, cats)
            } else {
                try {
                    ExpenseParsing.parse(client, text, cats)
                } catch (e: Exception) {
                    message = "${client.name}: ${e.message ?: e.javaClass.simpleName}. Использован простой разбор."
                    OfflineParser.parse(text, cats)
                }
            }
            busy = false
            if (parsed.isEmpty()) {
                // Ничего не распознали — откроем пустую форму, чтобы заполнить вручную
                draft = Draft(listOf(emptyItem()), text)
            } else {
                draft = Draft(parsed.map { it.toDraft(cats) }, text)
            }
        }
    }

    fun manualDraft() { draft = Draft(listOf(emptyItem()), "") }

    fun saveDraft(d: Draft) {
        val items = d.items.mapNotNull { it.toExpense(d.rawText) }
        if (items.isEmpty()) { message = "Нет корректных сумм — нечего сохранять"; return }
        viewModelScope.launch {
            val created = createSuggestedCategories(d.items.filter { it.toExpense(d.rawText) != null })
            c.db.expenses().insertAll(items)
            draft = null
            input = ""
            message = "Сохранено: ${items.size}" +
                if (created.isNotEmpty()) ". Новые категории: ${created.joinToString()}" else ""
        }
    }

    /** Создаёт предложенные моделью категории, которые пользователь оставил выбранными. Возвращает их имена. */
    private suspend fun createSuggestedCategories(items: List<DraftItem>): List<String> {
        val existing = c.db.categories().getAll()
        val existingNames = existing.map { it.name.lowercase() }.toSet()
        val toCreate = items
            .mapNotNull { item ->
                item.suggestedCategory?.takeIf { it.name == item.category }?.let { it to item.subcategory.trim() }
            }
            .filter { (cat, _) -> cat.name.lowercase() !in existingNames }
            .groupBy({ it.first.name }, { it })
            .values
            .mapIndexed { i, group ->
                val subs = group.map { it.second }.filter { it.isNotEmpty() }.distinct()
                // Новые категории встают перед «Прочее» (у него sortOrder = 99)
                val order = (existing.filter { it.name != DefaultCategories.OTHER }.maxOfOrNull { it.sortOrder } ?: -1) + 1 + i
                group.first().first.copy(subcategories = subs.joinToString(", "), sortOrder = order)
            }
        if (toCreate.isNotEmpty()) c.db.categories().insertIfAbsent(toCreate)
        return toCreate.map { it.name }
    }

    fun delete(e: Expense) = viewModelScope.launch { c.db.expenses().delete(e) }

    fun saveSettings(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        message = "Настройки сохранены"
    }

    fun testConnection(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        val client = clientFor(s) ?: run { message = "Выбран режим без ИИ"; return@launch }
        message = try {
            val r = ExpenseParsing.parse(client, "кофе двести пятьдесят и такси 400", c.db.categories().getAll())
            "${client.name} работает: " + r.joinToString { "${it.category} ${it.amount.toInt()}" }
        } catch (e: Exception) {
            "${client.name}: ошибка — ${e.message}"
        }
    }

    /** ИИ получает только агрегированные суммы по категориям, без мест и заметок. */
    fun requestInsight() {
        val client = clientFor(settings.value) ?: run { message = "Для комментария нужен ИИ (Настройки)"; return }
        val cur = totals.value
        if (cur.isEmpty()) { message = "За этот месяц трат нет"; return }
        val prev = prevTotals.value.associate { it.category to it.total }
        val summary = buildString {
            appendLine("Месяц: ${_month.value}. Траты по категориям (₽), в скобках — прошлый месяц:")
            cur.forEach { t -> appendLine("- ${t.category}: ${t.total / 100} (${(prev[t.category] ?: 0) / 100})") }
            appendLine("Итого: ${cur.sumOf { it.total } / 100} (прошлый месяц ${prev.values.sum() / 100})")
        }
        viewModelScope.launch {
            insightBusy = true
            insight = try {
                client.complete(
                    system = "Ты личный финансовый помощник. По сводке трат дай 3–5 коротких наблюдений на русском: " +
                        "что выросло или снизилось, где можно сэкономить. Без вступлений, маркированным списком.",
                    user = summary,
                    jsonMode = false,
                )
            } catch (e: Exception) {
                "Ошибка: ${e.message}"
            }
            insightBusy = false
        }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val all = c.db.expenses().getAll()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                w.write("﻿") // BOM, чтобы Excel правильно открыл кириллицу
                w.write("дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза\n")
                all.forEach { e ->
                    val date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.timestamp), zone)
                    val cols = listOf(
                        date.toString(), (e.amountKopecks / 100.0).toString().replace('.', ','), e.currency,
                        e.category, e.subcategory, e.place, e.note, e.rawText,
                    ).map { v -> "\"" + (v ?: "").replace("\"", "\"\"") + "\"" }
                    w.write(cols.joinToString(";") + "\n")
                }
            }
        }
        message = "Экспортировано записей: ${all.size}"
    }

    // --- Преобразования ---
    private fun YearMonth.startMillis(): Long = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun emptyItem() = DraftItem("", DefaultCategories.OTHER, "", "", "", LocalDate.now().toString())

    private fun ParsedExpense.toDraft(cats: List<Category>): DraftItem {
        val name = category.trim().take(40).replaceFirstChar { it.uppercaseChar() }
        val known = cats.firstOrNull { it.name.equals(name, ignoreCase = true) }?.name
        // Категории нет в списке — предлагаем создать новую (создастся при сохранении)
        val suggested = if (known == null && name.isNotBlank()) {
            Category(name = name, emoji = emoji?.trim()?.takeIf { it.isNotEmpty() && it.length <= 8 } ?: "🏷️")
        } else null
        val cat = known ?: suggested?.name ?: DefaultCategories.OTHER
        val amountStr = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        return DraftItem(amountStr, cat, subcategory.orEmpty(), place.orEmpty(), note.orEmpty(), d.toString(), suggested)
    }

    private fun DraftItem.toExpense(raw: String): Expense? {
        val amount = amountText.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val day = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: LocalDate.now()
        // Для сегодняшних трат берём текущее время, для прошлых дат — полдень
        val time = if (day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        return Expense(
            amountKopecks = (amount * 100).roundToLong(),
            category = category,
            subcategory = subcategory.trim().ifBlank { null },
            place = place.trim().ifBlank { null },
            note = note.trim().ifBlank { null },
            timestamp = day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
            rawText = raw.ifBlank { null },
        )
    }
}
