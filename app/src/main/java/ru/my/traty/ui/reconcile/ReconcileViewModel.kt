package ru.my.traty.ui.reconcile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.ReconcileException
import ru.my.traty.data.ReconcileOutcome
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.formatPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_LINE_NAME = "Всего"

/** Строка калькулятора остатка: название и сумма, как её ввёл пользователь. */
data class CalcLine(val id: Int, val name: String, val amountText: String)

/** Расхождение прошлой сверки для блока «динамика Неучтённых». */
data class HistoryItem(val label: String, val amountKopecks: Long)

/** Итог шага 2; [actualKopecks] - введённый остаток. */
data class ReconcileResultState(val outcome: ReconcileOutcome, val actualKopecks: Long)

/** Сколько должно было остаться, если оно известно из исхода сверки. */
fun ReconcileResultState.expectedKopecks(): Long? = when (outcome) {
    is ReconcileOutcome.Opening -> null
    ReconcileOutcome.Matched -> actualKopecks
    is ReconcileOutcome.Unaccounted -> actualKopecks + outcome.amountKopecks
    is ReconcileOutcome.Surplus -> actualKopecks - outcome.amountKopecks
}

class ReconcileViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()
    private var nextId = 0

    var lines by mutableStateOf(listOf(CalcLine(nextId++, DEFAULT_LINE_NAME, "")))
        private set
    /** Первая сверка: введённая сумма станет стартовым капиталом. */
    var isFirst by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var result by mutableStateOf<ReconcileResultState?>(null)
        private set
    /** Ставится синхронно в [confirm], до запуска корутины: повторное нажатие игнорируется. */
    var saving = false
        private set
    var savingUi by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    private var redoing = false

    val totalKopecks: Long get() = lines.sumOf { ReconcilePolicy.parseKopecks(it.amountText) ?: 0L }
    val canConfirm: Boolean get() = lines.any { ReconcilePolicy.parseKopecks(it.amountText) != null }

    /** Последние (до 6) сверки с расхождением «Неучтённые», новые первыми. */
    val history: StateFlow<List<HistoryItem>> = c.reconciliations.observeAll().map { all ->
        val startDay = c.settings.current().periodStartDay
        val expensesById = c.db.expenses().getAllWithCategory().associate { it.expense.id to it.expense.timestamp }
        all.filter { it.differenceKopecks < 0 }.takeLast(6).reversed().map { r ->
            val ts = r.expenseId?.let { expensesById[it] } ?: r.timestamp
            val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            HistoryItem(formatPeriod(BillingPeriod.containing(date, startDay), startDay), -r.differenceKopecks)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val names = c.settings.current().calculatorLines
            if (names.isNotEmpty()) lines = names.map { CalcLine(nextId++, it, "") }
            isFirst = c.reconciliations.last() == null
            loaded = true
        }
    }

    fun setName(id: Int, name: String) = update(id) { it.copy(name = name) }

    fun setAmount(id: Int, text: String) = update(id) { it.copy(amountText = text) }

    private fun update(id: Int, f: (CalcLine) -> CalcLine) {
        lines = lines.map { if (it.id == id) f(it) else it }
    }

    fun addLine() {
        lines = lines + CalcLine(nextId++, "", "")
    }

    fun removeLine(id: Int) {
        lines = lines.filterNot { it.id == id }.ifEmpty { listOf(CalcLine(nextId++, DEFAULT_LINE_NAME, "")) }
    }

    /** Вернуться к калькулятору, чтобы пересчитать; следующее «Готово» заменит последнюю сверку. */
    fun redo() {
        redoing = true
        result = null
        lines = lines.map { it.copy(amountText = "") }
    }

    fun confirm() {
        if (saving || result != null || !canConfirm) return
        saving = true
        savingUi = true
        val actual = totalKopecks
        val redo = redoing
        val names = lines.map { it.name }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val outcome = if (redo) {
                    c.reconciliations.redoLast(actual, now, zone)
                } else {
                    c.reconciliations.reconcile(actual, now, entryDateNow(), zone)
                }
                c.settings.setCalculatorLines(names)
                redoing = false
                result = ReconcileResultState(outcome, actual)
            } catch (e: ReconcileException) {
                message = e.message
            } finally {
                saving = false
                savingUi = false
            }
        }
    }

    private suspend fun entryDateNow(): LocalDate {
        val startDay = c.settings.current().periodStartDay
        val last = c.reconciliations.last()?.let { dateOf(it.timestamp) }
        val first = listOfNotNull(c.db.expenses().getFirstTimestamp(), c.db.incomes().getFirstTimestamp())
            .minOrNull()?.let(::dateOf)
        return ReconcilePolicy.entryDate(LocalDate.now(zone), startDay, last, first)
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

/** Решает, показывать ли на Главной карточку «Период закончился…», и откладывает её. */
class ReconcilePromptViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()
    private val tick = MutableStateFlow(0)

    val showCard: StateFlow<Boolean> = combine(c.settings.flow, c.reconciliations.observeAll(), tick) { s, _, _ ->
        val last = c.reconciliations.last()?.let { dateOf(it.timestamp) }
        val reference = last ?: listOfNotNull(c.db.expenses().getFirstTimestamp(), c.db.incomes().getFirstTimestamp())
            .minOrNull()?.let(::dateOf)
        val snooze = s.reconcileSnoozeUntilEpochDay.takeIf { it > 0 }?.let(LocalDate::ofEpochDay)
        ReconcilePolicy.shouldPrompt(LocalDate.now(zone), s.periodStartDay, reference, snooze)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Пересчитать (например, при возврате в приложение: мог начаться новый день). */
    fun refresh() = tick.update { it + 1 }

    /** «Напомнить завтра»: карточка скрыта сегодня и снова видна со следующего дня. */
    fun snooze() {
        viewModelScope.launch { c.settings.setReconcileSnoozeUntil(LocalDate.now(zone).toEpochDay()) }
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
