package ru.my.traty.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Системное распознавание Android (Google и др.) без отдельного окна — запасной движок,
 * пока не скачана модель GigaAM. Вызывать с главного потока.
 */
class SystemSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechListener? = null
    private var lastPartial = ""

    override fun start(listener: SpeechListener) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError(IllegalStateException("На телефоне нет сервиса распознавания речи"))
            return
        }
        this.listener = listener
        lastPartial = ""
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                this@SystemSpeechEngine.listener?.onRecognizing()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB примерно от -2 до 10
                this@SystemSpeechEngine.listener?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) {
                    lastPartial = t
                    this@SystemSpeechEngine.listener?.onPartial(t)
                }
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                this@SystemSpeechEngine.listener?.onFinal(t.ifBlank { lastPartial })
            }

            override fun onError(error: Int) {
                val l = this@SystemSpeechEngine.listener ?: return
                when (error) {
                    // Тишина или нечего распознавать — не ошибка сервиса: отдаём то, что успели услышать
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> l.onFinal(lastPartial)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> l.onError(IllegalStateException("Нужен доступ к микрофону"))
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        l.onError(IllegalStateException("Системному распознаванию нужен интернет"))
                    else -> l.onError(IllegalStateException("Ошибка распознавания речи ($error)"))
                }
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        listener = null
        recognizer?.cancel()
    }

    override fun release() {
        listener = null
        recognizer?.destroy()
        recognizer = null
    }
}
