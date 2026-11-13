package ru.my.traty.speech

import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Этап 1: системное распознавание Android (окно Google/«Говоруна»/другого сервиса).
 * Этап 2: сюда добавится реализация SpeechEngine на GigaAM v3 + Silero VAD через sherpa-onnx,
 * и экран ввода переключится на неё без изменений остального кода.
 */
object SystemSpeech {
    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите трату, например «кофе 250»")
    }
}

/** Контракт для будущего встроенного движка (GigaAM). */
interface SpeechEngine {
    /** Начать запись; распознанный текст придёт в onResult. */
    fun start(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (Throwable) -> Unit)
    fun stop()
    fun release()
}
