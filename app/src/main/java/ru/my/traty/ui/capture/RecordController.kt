package ru.my.traty.ui.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.my.traty.speech.SpeechEngine
import ru.my.traty.speech.SpeechListener

sealed interface RecordState {
    data object Idle : RecordState
    data class Listening(val partial: String = "", val level: Float = 0f) : RecordState
    data class Error(val message: String) : RecordState
}

/**
 * Управляет одной записью: на каждую запись создаётся новый [SpeechEngine] (GigaAM держит модель в памяти
 * только пока идёт запись). Используется главной и панелью быстрой записи.
 * [onText] получает итоговый непустой текст.
 */
class RecordController(
    private val engineFactory: () -> SpeechEngine,
    private val onText: (String) -> Unit,
) {
    private val _state = MutableStateFlow<RecordState>(RecordState.Idle)
    val state: StateFlow<RecordState> = _state.asStateFlow()

    private var engine: SpeechEngine? = null

    /** Нажатие на кнопку: начать запись или закончить текущую. */
    fun toggle() {
        when (_state.value) {
            is RecordState.Listening -> engine?.stop()
            else -> start()
        }
    }

    fun cancel() {
        val e = engine ?: run { _state.value = RecordState.Idle; return }
        engine = null
        e.cancel()
        e.release()
        _state.value = RecordState.Idle
    }

    private fun start() {
        val e = engineFactory()
        engine = e
        _state.value = RecordState.Listening()
        e.start(object : SpeechListener {
            override fun onPartial(text: String) {
                if (engine !== e) return
                _state.value = RecordState.Listening(text, (_state.value as? RecordState.Listening)?.level ?: 0f)
            }

            override fun onLevel(level: Float) {
                if (engine !== e) return
                val cur = _state.value as? RecordState.Listening ?: return
                _state.value = cur.copy(level = level)
            }

            override fun onFinal(text: String) {
                if (engine !== e) return
                finish(e)
                if (text.isBlank()) {
                    _state.value = RecordState.Error("Не расслышал, повторите")
                } else {
                    _state.value = RecordState.Idle
                    onText(text.trim())
                }
            }

            override fun onError(error: Throwable) {
                if (engine !== e) return
                finish(e)
                _state.value = RecordState.Error(error.message ?: "Ошибка распознавания речи")
            }
        })
    }

    private fun finish(e: SpeechEngine) {
        engine = null
        e.release()
    }
}
