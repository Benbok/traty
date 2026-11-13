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
import ru.my.traty.backup.CsvBackup
import ru.my.traty.backup.CsvImporter
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ThemeMode
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.normalizeName
import ru.my.traty.data.sameName
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

/** Новая категория, предложенная моделью (её ещё нет в базе). */
data class NewCategory(val name: String, val emoji: String)

data class DraftItem(
    val amountText: String,
    val category: String,
    val subcategory: String,
    val place: String,
    val note: String,
    val dateText: String,
    /** Создаётся при сохранении, если пользователь оставил её выбранной. */
    val suggestedCategory: NewCategory? = null,
)

data class Draft(val items: List<DraftItem>, val rawText: String, val source: String = ExpenseSource.TEXT)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    val settings: StateFlow<AppSettings> =
        c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    /** Активные (не архивные) категории — для выбора и для промпта. */
    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val expenses: StateFlow<List<ExpenseWithCategory>> = _month.flatMapLatest { m ->
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
    /** Текущий текст в поле пришёл из распознавания речи */
    private var inputFromVoice = false
    var busy by mutableStateOf(false)
        private set
    var draft by mutableStateOf<Draft?>(null)
    var message by mutableStateOf<String?>(null)
    var insight by mutableStateOf<String?>(null)
        private set
    var insightBusy by mutableStateOf(false)
        private set

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
        if (date != null) {
            val ym = YearMonth.from(date)
            if (_month.value != ym) {
                _month.value = ym
                insight = null
            }
        }
    }

    fun clearSelectedDate() {
        _selectedDate.value = null
    }

    fun prevPeriod() {
        val cur = _selectedDate.value
        if (cur != null) {
            selectDate(cur.minusDays(1))
        } else {
            prevMonth()
        }
    }

    fun nextPeriod() {
        val cur = _selectedDate.value
        if (cur != null) {
            selectDate(cur.plusDays(1))
        } else {
            nextMonth()
        }
    }

    fun prevMonth() {
        _selectedDate.value = null
        _month.value = _month.value.minusMonths(1)
        insight = null
    }

    fun nextMonth() {
        _selectedDate.value = null
        _month.value = _month.value.plusMonths(1)
        insight = null
    }

    /** Результат распознавания речи: подставляем и сразу разбираем (подтверждение всё равно будет). */
    fun onVoiceResult(text: String) {
        input = text
        inputFromVoice = true
        parseInput()
    }

    /** Текст (набранный или надиктованный) → черновик трат для подтверждения. */
    fun parseInput() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val source = if (inputFromVoice) ExpenseSource.VOICE else ExpenseSource.TEXT
        viewModelScope.launch {
            busy = true
            val cats = c.db.categories().getActiveWithSubs()
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
            draft = if (parsed.isEmpty()) {
                // Ничего не распознали — откроем пустую форму, чтобы заполнить вручную
                Draft(listOf(emptyItem()), text, source)
            } else {
                Draft(parsed.map { it.toDraft(cats) }, text, source)
            }
        }
    }

    fun manualDraft() { draft = Draft(listOf(emptyItem()), "", ExpenseSource.MANUAL) }

    fun saveDraft(d: Draft) {
        val valid = d.items.mapNotNull { item -> item.amountAndTime()?.let { item to it } }
        if (valid.isEmpty()) { message = "Нет корректных сумм — нечего сохранять"; return }
        viewModelScope.launch {
            val known = c.db.categories().getAllWithSubs().map { it.category.name }
            val expenses = valid.map { (item, at) ->
                val ids = c.categories.resolveForSave(
                    categoryName = item.category,
                    newCategoryEmoji = item.suggestedCategory?.takeIf { it.name == item.category }?.emoji,
                    subcategoryName = item.subcategory.ifBlank { null },
                )
                Expense(
                    amountKopecks = at.first,
                    categoryId = ids.categoryId,
                    subcategoryId = ids.subcategoryId,
                    place = item.place.trim().ifBlank { null },
                    note = item.note.trim().ifBlank { null },
                    timestamp = at.second,
                    rawText = d.rawText.ifBlank { null },
                    source = d.source,
                )
            }
            c.db.expenses().insertAll(expenses)
            val created = valid.map { it.first.category }
                .filter { name -> known.none { sameName(it, name) } }
                .map { normalizeName(it) }
                .distinct()
            draft = null
            input = ""
            inputFromVoice = false
            message = "Сохранено: ${expenses.size}" +
                if (created.isNotEmpty()) ". Новые категории: ${created.joinToString()}" else ""
        }
    }

    fun delete(e: Expense) = viewModelScope.launch { c.db.expenses().delete(e) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        c.settings.setThemeMode(mode)
    }

    fun saveSettings(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        message = "Настройки сохранены"
    }

    fun testConnection(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        val client = clientFor(s) ?: run { message = "Выбран режим без ИИ"; return@launch }
        message = try {
            val r = ExpenseParsing.parse(client, "кофе двести пятьдесят и такси 400", c.db.categories().getActiveWithSubs())
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
        val prev = prevTotals.value.associate { it.categoryId to it.total }
        val summary = buildString {
            appendLine("Месяц: ${_month.value}. Траты по категориям (₽), в скобках — прошлый месяц:")
            cur.forEach { t -> appendLine("- ${t.name}: ${t.total / 100} (${(prev[t.categoryId] ?: 0) / 100})") }
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
        val all = c.db.expenses().getAllWithCategory()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                CsvBackup.write(all, zone, w)
            }
        }
        message = "Экспортировано записей: ${all.size}"
    }

    /** Восстановление из CSV (своего экспорта или пересохранённого в Excel). Уже существующие траты пропускаются. */
    fun importCsv(uri: Uri) = viewModelScope.launch {
        message = try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.let(CsvBackup::decode)
            } ?: throw IllegalStateException("не удалось открыть файл")
            val r = CsvImporter(c.db, c.categories).import(CsvBackup.parse(text, zone))
            buildString {
                append("Импортировано: ${r.imported}, пропущено дублей: ${r.duplicates}")
                if (r.badLines.isNotEmpty()) {
                    append(", строки с ошибками: ")
                    append(r.badLines.take(10).joinToString())
                    if (r.badLines.size > 10) append("…")
                }
            }
        } catch (e: Exception) {
            "Импорт не удался: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // --- Преобразования ---
    private fun YearMonth.startMillis(): Long = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun emptyItem() = DraftItem("", DefaultCategories.OTHER, "", "", "", LocalDate.now().toString())

    private fun ParsedExpense.toDraft(cats: List<CategoryWithSubs>): DraftItem {
        val name = normalizeName(category)
        val known = cats.firstOrNull { sameName(it.category.name, name) }?.category?.name
        // Категории нет в списке — предлагаем создать новую (создастся при сохранении)
        val suggested = if (known == null && name.isNotBlank()) {
            NewCategory(name, emoji?.trim()?.takeIf { it.isNotEmpty() && it.length <= 8 } ?: DefaultCategories.NEW_EMOJI)
        } else null
        val cat = known ?: suggested?.name ?: DefaultCategories.OTHER
        val amountStr = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        return DraftItem(amountStr, cat, subcategory.orEmpty(), place.orEmpty(), note.orEmpty(), d.toString(), suggested)
    }

    /** Сумма в копейках и время траты, или null, если сумма некорректна. */
    private fun DraftItem.amountAndTime(): Pair<Long, Long>? {
        val amount = amountText.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val day = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: LocalDate.now()
        // Для сегодняшних трат берём текущее время, для прошлых дат — полдень
        val time = if (day == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        return (amount * 100).roundToLong() to day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
