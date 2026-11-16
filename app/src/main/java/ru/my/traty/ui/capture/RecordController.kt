package ru.my.traty.ui.capture

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.my.traty.speech.SpeechEngine
import ru.my.traty.speech.SpeechListener

sealed interface RecordState {
    data object Idle : RecordState
    data class Listening(val partial: String = "", val level: Float = 0f) : RecordState

    /**
     * Запись закончена (нажали «стоп» или движок услышал паузу), идёт распознавание хвоста фразы.
     * Для длинного списка это несколько секунд; нажатия на кнопку в это время ничего не делают.
     */
    data class Recognizing(val partial: String = "") : RecordState

    data class Error(val message: String) : RecordState
}

/**
 * Управляет одной записью: на каждую запись создаётся новый [SpeechEngine] (GigaAM держит модель в памяти
 * только пока идёт запись). Используется главной и панелью быстрой записи.
 * [onText] получает итоговый непустой текст.
 *
 * Итог записи (текст или ошибка) принимается на главном потоке через [mainThread]: так конец записи
 * и начало разбора ([onText] ставит «Разбираю…») происходят в одном шаге, и кнопка не освобождается между ними.
 */
class RecordController(
    private val engineFactory: () -> SpeechEngine,
    private val cues: RecordCues = RecordCues.None,
    private val mainThread: (() -> Unit) -> Unit = ::runOnMainThread,
    private val onText: (String) -> Unit,
) {
    private val _state = MutableStateFlow<RecordState>(RecordState.Idle)
    val state: StateFlow<RecordState> = _state.asStateFlow()

    private var engine: SpeechEngine? = null
    private var engineStarted = false

    /** Нажатие на кнопку: начать запись или закончить текущую. Пока идёт распознавание — ничего. */
    fun toggle() {
        when (val s = _state.value) {
            is RecordState.Listening ->
                if (engineStarted) {
                    engine?.stop()
                    _state.value = RecordState.Recognizing(s.partial)
                } else {
                    cancel() // нажали во время сигнала — отмена
                }
            is RecordState.Recognizing -> Unit
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
        engineStarted = false
        _state.value = RecordState.Listening()
        // Микрофон включаем после сигнала начала, чтобы он не попал в запись
        cues.playStart { if (engine === e) startEngine(e) }
    }

    private fun startEngine(e: SpeechEngine) {
        engineStarted = true
        e.start(object : SpeechListener {
            override fun onPartial(text: String) {
                if (engine !== e) return
                _state.update { cur ->
                    when (cur) {
                        is RecordState.Recognizing -> cur.copy(partial = text)
                        is RecordState.Listening -> cur.copy(partial = text)
                        else -> cur
                    }
                }
            }

            override fun onLevel(level: Float) {
                if (engine !== e) return
                _state.update { cur -> if (cur is RecordState.Listening) cur.copy(level = level) else cur }
            }

            override fun onRecognizing() {
                if (engine !== e) return
                _state.update { cur -> if (cur is RecordState.Listening) RecordState.Recognizing(cur.partial) else cur }
            }

            override fun onFinal(text: String) {
                mainThread {
                    // Запись могли отменить, пока итог шёл на главный поток
                    if (engine === e) {
                        finish(e)
                        if (text.isBlank()) {
                            _state.value = RecordState.Error("Не расслышал, повторите")
                        } else {
                            cues.playEnd()
                            onText(text.trim())
                            _state.value = RecordState.Idle
                        }
                    }
                }
            }

            override fun onError(error: Throwable) {
                mainThread {
                    if (engine === e) {
                        finish(e)
                        _state.value = RecordState.Error(error.message ?: "Ошибка распознавания речи")
                    }
                }
            }
        })
    }

    private fun finish(e: SpeechEngine) {
        engine = null
        e.release()
    }
}

/** На главном потоке выполняет сразу, с другого потока ставит в очередь главного. */
private fun runOnMainThread(block: () -> Unit) {
    val main = Looper.getMainLooper()
    if (Looper.myLooper() == main) block() else Handler(main).post(block)
}
