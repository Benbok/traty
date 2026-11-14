package ru.my.traty.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.EntryOrigin
import ru.my.traty.capture.EntryOutcome
import ru.my.traty.capture.SaveResult
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.selectable

sealed interface CaptureUi {
    /** Идёт запись (кнопка микрофона). */
    data object Recording : CaptureUi

    /** Нужна проверка человеком. */
    data class Confirm(val draft: EntryDraft, val notice: String?) : CaptureUi

    /** Сохранено без подтверждения; на пару секунд показываем «Изменить». */
    data class Saved(val result: SaveResult, val draft: EntryDraft) : CaptureUi

    /** Нет сети: фраза ушла в очередь. */
    data object Queued : CaptureUi
}

/** Состояние панели быстрой записи. Сохранение — через общий [ru.my.traty.capture.QuickEntry]. */
class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    val settings: StateFlow<AppSettings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().map { it.selectable() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow<CaptureUi>(CaptureUi.Recording)
    val ui: StateFlow<CaptureUi> = _ui.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _finish = MutableStateFlow(false)
    /** true — панель пора закрыть. */
    val finishRequested: StateFlow<Boolean> = _finish.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onSpokenText(text: String): Job {
        val phrase = text.trim()
        if (phrase.isEmpty() || _busy.value) return doneJob()
        return viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val outcome = try {
                c.quickEntry.process(phrase, EntryOrigin.VOICE)
            } finally {
                _busy.value = false
            }
            when (outcome) {
                is EntryOutcome.AutoSaved -> _ui.value = CaptureUi.Saved(outcome.result, outcome.draft)
                is EntryOutcome.NeedsConfirm -> _ui.value = CaptureUi.Confirm(outcome.draft, outcome.notice)
                is EntryOutcome.Queued -> _ui.value = CaptureUi.Queued
            }
        }
    }

    /** «Изменить» после автосохранения: открыть сохранённое на проверку, панель остаётся. */
    fun editSaved(): Job {
        val saved = _ui.value as? CaptureUi.Saved ?: return doneJob()
        return viewModelScope.launch {
            val draft = c.quickEntry.draftFor(saved.result.saved, saved.draft.rawText, saved.draft.origin)
            _ui.value = CaptureUi.Confirm(draft, null)
        }
    }

    fun save(draft: EntryDraft): Job = viewModelScope.launch {
        val result = c.quickEntry.save(draft)
        if (result.saved.count == 0) {
            _error.value = "Нет корректных сумм — нечего сохранять"
            return@launch
        }
        _finish.value = true
    }

    fun reportError(message: String) {
        _error.value = message
    }

    /** Закрыть панель. Таймер «через 2 секунды после Saved/Queued» живёт в UI и сам отменяется при смене состояния. */
    fun finish() {
        _finish.value = true
    }

    private fun doneJob(): Job = Job().also { it.complete() }
}
