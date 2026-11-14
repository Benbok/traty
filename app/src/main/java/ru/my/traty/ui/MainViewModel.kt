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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import kotlinx.serialization.json.Json
import ru.my.traty.ai.EntryParsing
import ru.my.traty.ai.clientFor
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.EntryOrigin
import ru.my.traty.capture.EntryOutcome
import ru.my.traty.capture.RuleOffer
import ru.my.traty.capture.SavedEntries
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.VoiceEngine
import ru.my.traty.speech.ModelState
import ru.my.traty.backup.CsvBackup
import ru.my.traty.backup.CsvImporter
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ThemeMode
import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.isUserEditable
import ru.my.traty.data.Rule
import ru.my.traty.data.RuleWithNames
import ru.my.traty.data.Template
import ru.my.traty.data.TemplateWithCategory
import ru.my.traty.ui.home.EditFields
import ru.my.traty.data.selectable
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.data.labels.ExpenseLabeler
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.recurring.RecurringRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Последний автосохранённый ввод: по нему работает «Изменить» в снекбаре. */
data class LastSaved(val saved: SavedEntries, val rawText: String, val origin: EntryOrigin)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    val settings: StateFlow<AppSettings> =
        c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    /** Активные (не архивные) категории — для выбора и для промпта. */
    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().map { it.selectable() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Метки на тратах (🔺 🔁 🆕 ⚡ 📅): считаются по всей истории, не только по показанному периоду.
     * Живёт, только пока экран на него подписан, поэтому фоновых расчётов без UI нет.
     */
    val labels: StateFlow<Map<Long, List<ExpenseLabelHit>>> =
        combine(
            c.db.expenses().observeAllWithCategory(),
            c.db.recurring().observeAll(),
            c.db.categories().observeActiveWithSubs(),
            c.settings.flow.map { it.labelsEnabled }.distinctUntilChanged(),
        ) { expenses, recurring, cats, enabled ->
            if (!enabled) return@combine emptyMap()
            val candidates = RecurringRepository.suggest(expenses, recurring, cats)
            ExpenseLabeler.label(
                expenses,
                ExpenseLabeler.subscriptionExpenseIds(expenses, recurring, candidates),
                zone,
                impulsiveCategories = cats.filter { it.category.isImpulsive }.map { it.category.name },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Шаблоны быстрых трат — чипсы над строкой ввода. */
    val templates: StateFlow<List<TemplateWithCategory>> =
        c.db.templates().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Правила «место → категория». */
    val rules: StateFlow<List<RuleWithNames>> =
        c.db.rules().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** День начала расчётного периода из настроек (1 — календарный месяц). */
    private var startDay = 1
    private val _period = MutableStateFlow(BillingPeriod.containing(LocalDate.now(), startDay))
    val period: StateFlow<BillingPeriod> = _period

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val expenses: StateFlow<List<ExpenseWithCategory>> = _period.flatMapLatest { p ->
        c.db.expenses().observeRange(p.startMillis(zone), p.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Доходы текущего периода. */
    val incomes: StateFlow<List<Income>> = _period.flatMapLatest { p ->
        c.db.incomes().observeRange(p.startMillis(zone), p.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totals: StateFlow<List<CategoryTotal>> = _period.flatMapLatest { p ->
        c.db.expenses().observeTotals(p.startMillis(zone), p.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Суммы по (категория, место) текущего периода — для разбивки по магазинам в статистике. */
    val placeTotals: StateFlow<List<ru.my.traty.data.PlaceTotal>> = _period.flatMapLatest { p ->
        c.db.expenses().observePlaceTotals(p.startMillis(zone), p.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prevTotals: StateFlow<List<CategoryTotal>> = _period.flatMapLatest { p ->
        val prev = p.previous(startDay)
        c.db.expenses().observeTotals(prev.startMillis(zone), prev.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Траты прошлого расчётного периода — для линии накопленных трат «сейчас против прошлого». */
    val prevExpenses: StateFlow<List<ExpenseWithCategory>> = _period.flatMapLatest { p ->
        val prev = p.previous(startDay)
        c.db.expenses().observeRange(prev.startMillis(zone), prev.endMillisExclusive(zone))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Дни прошлого периода, с которыми честно сравнивать категории: пока период идёт — столько же первых дней,
     * сколько прошло в текущем (см. [ru.my.traty.data.PeriodComparison.comparableWindow]).
     */
    val comparableWindow: StateFlow<Pair<LocalDate, LocalDate>> = _period.map { p ->
        ru.my.traty.data.PeriodComparison.comparableWindow(p, p.previous(startDay), LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now() to LocalDate.now())

    /** Траты по категориям за [comparableWindow] — для роста и падения по категориям и «Что изменилось». */
    val comparableTotals: StateFlow<List<CategoryTotal>> = comparableWindow.flatMapLatest { (from, to) ->
        c.db.expenses().observeTotals(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Фразы без сети (ждут разбора) и разобранные в фоне (ждут проверки). */
    val pending: StateFlow<List<PendingPhrase>> =
        c.db.pendingPhrases().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Состояние экрана ---
    var busy by mutableStateOf(false)
        private set
    /** Черновик фразы, ожидающий проверки (экран подтверждения). */
    var confirm by mutableStateOf<EntryDraft?>(null)
    var confirmNotice by mutableStateOf<String?>(null)
        private set
    /** Фраза из очереди, открытая на проверку: удаляется из очереди после сохранения. */
    private var confirmPendingId: Long? = null
    /** Что автосохранилось последним — для кнопки «Изменить» в снекбаре. */
    var lastSaved by mutableStateOf<LastSaved?>(null)
    var message by mutableStateOf<String?>(null)
    /** Трата, открытая для редактирования. */
    var editing by mutableStateOf<ExpenseWithCategory?>(null)
    /** Последняя удалённая трата для Undo. */
    var deletedForUndo by mutableStateOf<Expense?>(null)
        private set
    /** Трата, для которой предлагается создать правило «место → категория». */
    var ruleOffer by mutableStateOf<RuleOffer?>(null)

    var insight by mutableStateOf<String?>(null)
        private set
    var insightBusy by mutableStateOf(false)
        private set

    init {
        // Смена дня начала периода в настройках: пересчитываем текущий период
        viewModelScope.launch {
            settings.map { it.periodStartDay }.distinctUntilChanged().collect { day ->
                if (day != startDay) {
                    startDay = day
                    _period.value = BillingPeriod.containing(_selectedDate.value ?: LocalDate.now(), day)
                    insight = null
                }
            }
        }
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
        if (date != null && date !in _period.value) {
            _period.value = BillingPeriod.containing(date, startDay)
            insight = null
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
            prevBillingPeriod()
        }
    }

    fun nextPeriod() {
        val cur = _selectedDate.value
        if (cur != null) {
            selectDate(cur.plusDays(1))
        } else {
            nextBillingPeriod()
        }
    }

    fun prevBillingPeriod() {
        _selectedDate.value = null
        _period.value = _period.value.previous(startDay)
        insight = null
    }

    fun nextBillingPeriod() {
        _selectedDate.value = null
        _period.value = _period.value.next(startDay)
        insight = null
    }

    /**
     * Распознанная голосом фраза → разбор → автосохранение, экран подтверждения или очередь (нет сети).
     * Возвращает задачу, чтобы тест мог дождаться результата.
     */
    fun onSpokenText(text: String): Job {
        val phrase = text.trim()
        if (phrase.isEmpty() || busy) return Job().also { it.complete() }
        return viewModelScope.launch {
            busy = true
            val outcome = try {
                c.quickEntry.process(phrase, EntryOrigin.VOICE)
            } finally {
                busy = false
            }
            when (outcome) {
                is EntryOutcome.AutoSaved -> {
                    lastSaved = LastSaved(outcome.result.saved, phrase, EntryOrigin.VOICE)
                    ruleOffer = outcome.result.ruleOffer
                    message = "Сохранено ${outcome.result.saved.count}"
                }
                is EntryOutcome.NeedsConfirm -> {
                    confirmPendingId = null
                    confirmNotice = outcome.notice
                    confirm = outcome.draft
                }
                is EntryOutcome.Queued -> message = "Нет сети, разберу позже"
            }
        }
    }

    /** «Изменить» после автосохранения: открывает сохранённое на проверку; сохранение заменит старые записи. */
    fun editLastSaved(): Job {
        val last = lastSaved ?: return Job().also { it.complete() }
        lastSaved = null
        return viewModelScope.launch {
            confirmPendingId = null
            confirmNotice = null
            confirm = c.quickEntry.draftFor(last.saved, last.rawText, last.origin)
        }
    }

    /** Разобранная в фоне фраза из очереди → экран подтверждения. */
    fun openParsedPending(p: PendingPhrase) {
        val json = p.resultJson ?: return
        val draft = runCatching { Json.decodeFromString<EntryDraft>(json) }.getOrNull() ?: return
        confirmPendingId = p.id
        confirmNotice = null
        confirm = draft
    }

    fun dismissConfirm() {
        confirm = null
        confirmNotice = null
        confirmPendingId = null
    }

    /** Сохранение черновика с экрана подтверждения: траты и пополнения одним списком. */
    private var savingEntry = false

    /** Двойной тап на «Сохранить» не должен создать дубли: пока идёт сохранение, повторные вызовы игнорируются. */
    fun saveEntry(d: EntryDraft): Job {
        if (savingEntry) return Job().also { it.complete() }
        savingEntry = true
        return viewModelScope.launch {
            try {
                val result = c.quickEntry.save(d)
                if (result.saved.count == 0) { message = "Нет корректных сумм — нечего сохранять"; return@launch }
                confirmPendingId?.let { c.db.pendingPhrases().delete(it) }
                dismissConfirm()
                ruleOffer = result.ruleOffer
                message = "Сохранено: ${result.saved.count}" +
                    (if (result.newCategories.isNotEmpty()) ". Новые категории: ${result.newCategories.joinToString()}" else "") +
                    (if (result.recurringAdded.isNotEmpty()) ". В подписки добавлено: ${result.recurringAdded.joinToString()}" else "")
            } finally {
                savingEntry = false
            }
        }
    }

    fun delete(e: Expense) = viewModelScope.launch {
        if (e.isUserEditable) {
            c.db.expenses().delete(e)
            deletedForUndo = e
            message = "Трата удалена"
        }
    }

    fun undoDelete() {
        val e = deletedForUndo ?: return
        deletedForUndo = null
        viewModelScope.launch { c.db.expenses().insert(e) }
    }

    fun clearDeletedForUndo() { deletedForUndo = null }

    /** Мгновенное сохранение траты по шаблону — 1 нажатие. */
    fun useTemplate(t: TemplateWithCategory) = viewModelScope.launch {
        val e = Expense(
            amountKopecks = t.template.amountKopecks,
            categoryId = t.template.categoryId,
            subcategoryId = t.template.subcategoryId,
            place = t.template.place,
            note = t.template.title,
            timestamp = ru.my.traty.data.entryTimestamp(LocalDate.now(), zone),
            source = ExpenseSource.MANUAL,
        )
        c.db.expenses().insert(e)
        message = "${t.categoryEmoji} ${t.template.title}: ${t.template.amountKopecks / 100} ₽"
    }

    fun addTemplate(title: String, amountKopecks: Long, categoryId: Long, subcategoryId: Long?, place: String?) =
        viewModelScope.launch {
            c.db.templates().insert(
                Template(
                    title = title.trim(),
                    amountKopecks = amountKopecks,
                    categoryId = categoryId,
                    subcategoryId = subcategoryId,
                    place = ru.my.traty.data.Places.normalize(place),
                )
            )
            message = "Шаблон добавлен"
        }

    fun deleteTemplate(t: Template) = viewModelScope.launch {
        c.db.templates().delete(t)
        message = "Шаблон удалён"
    }

    /** Создать правило «место → категория». */
    fun addRule(pattern: String, categoryId: Long, subcategoryId: Long?) = viewModelScope.launch {
        val normalized = pattern.trim().lowercase()
        if (normalized.isEmpty()) return@launch
        val existing = c.db.rules().findByPattern(normalized)
        if (existing != null) {
            c.db.rules().update(existing.copy(categoryId = categoryId, subcategoryId = subcategoryId))
            message = "Правило обновлено"
        } else {
            c.db.rules().insert(Rule(pattern = normalized, categoryId = categoryId, subcategoryId = subcategoryId))
            message = "Правило создано: «$normalized»"
        }
    }

    fun deleteRule(r: Rule) = viewModelScope.launch {
        c.db.rules().delete(r)
    }

    /** Обновление существующей траты из диалога редактирования. */
    fun updateExpense(original: ExpenseWithCategory, fields: EditFields) {
        val e = original.expense
        if (!e.isUserEditable) return
        val amount = fields.amountText.replace(',', '.').replace(" ", "").toDoubleOrNull()
        if (amount == null || amount <= 0) { message = "Некорректная сумма"; return }
        val day = runCatching { LocalDate.parse(fields.dateText.trim()) }.getOrNull()
        if (day == null) { message = "Некорректная дата"; return }
        viewModelScope.launch {
            val place = ru.my.traty.data.Places.canonical(fields.place, c.db.expenses().distinctPlaces())
            val ids = c.categories.resolveForSave(
                categoryName = fields.category,
                newCategoryEmoji = null,
                subcategoryName = fields.subcategory.ifBlank { null },
            )
            val updated = e.copy(
                amountKopecks = (amount * 100).roundToLong(),
                categoryId = ids.categoryId,
                subcategoryId = ids.subcategoryId,
                place = place,
                note = fields.note.trim().ifBlank { null },
                timestamp = ru.my.traty.data.entryTimestamp(day, zone),
            )
            c.db.expenses().update(updated)
            editing = null
            message = "Трата обновлена"
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        c.settings.setThemeMode(mode)
    }

    /** Состояние модели распознавания речи GigaAM. */
    val modelState: StateFlow<ModelState> = c.models.state

    fun refreshModel() = c.models.refresh()

    /** Качается в процессном скоупе, а не в viewModelScope: уход с экрана настроек не прерывает скачивание. */
    fun downloadModel() = c.downloadModel()

    fun deleteModel() = c.models.delete()

    fun setLabelsEnabled(enabled: Boolean) = viewModelScope.launch {
        c.settings.setLabelsEnabled(enabled)
    }

    fun setVoiceEngine(engine: VoiceEngine) = viewModelScope.launch {
        c.settings.setVoiceEngine(engine)
    }

    fun setPeriodStartDay(day: Int) = viewModelScope.launch {
        c.settings.setPeriodStartDay(day)
    }

    fun saveSettings(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        message = "Настройки сохранены"
    }

    fun testConnection(s: AppSettings) = viewModelScope.launch {
        c.settings.save(s)
        val client = clientFor(s) ?: run { message = "Выбран режим без ИИ"; return@launch }
        message = try {
            val r = EntryParsing.parse(
                client, "кофе двести пятьдесят и такси 400",
                c.db.categories().getActiveWithSubs().selectable(), emptyList(), LocalDate.now(),
            )
            "${client.name} работает: " + r.joinToString { "${it.category} ${it.amount.toInt()}" }
        } catch (e: Exception) {
            "${client.name}: ошибка — ${e.message}"
        }
    }

    /** ИИ получает только агрегированные суммы по категориям, без мест и заметок. */
    fun requestInsight() {
        val client = clientFor(settings.value) ?: run { message = "Для комментария нужен ИИ (Настройки)"; return }
        val cur = totals.value
        if (cur.isEmpty()) { message = "За этот период трат нет"; return }
        val prev = prevTotals.value.associate { it.categoryId to it.total }
        val summary = buildString {
            appendLine("Период: ${formatPeriod(_period.value, startDay)}. Траты по категориям (₽), в скобках — прошлый период:")
            cur.forEach { t -> appendLine("- ${t.name}: ${t.total / 100} (${(prev[t.categoryId] ?: 0) / 100})") }
            appendLine("Итого: ${cur.sumOf { it.total } / 100} (прошлый период ${prev.values.sum() / 100})")
        }
        viewModelScope.launch {
            insightBusy = true
            insight = try {
                client.complete(
                    system = "Ты личный финансовый помощник. По сводке трат дай 3–5 коротких наблюдений на русском: " +
                        "что выросло или снизилось, где можно сэкономить. Без вступлений, маркированным списком. " +
                        "Если в сводке есть категория «Неучтённые» — говори о ней спокойно и поддерживающе, без упрёков; " +
                        "это деньги, потраченные вне записей.",
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
        val incomes = c.db.incomes().getAll()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                CsvBackup.write(all, incomes, zone, w)
            }
        }
        message = "Экспортировано записей: ${all.size + incomes.size}"
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
}
