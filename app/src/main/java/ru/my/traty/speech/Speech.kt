package ru.my.traty.speech

import android.content.Context
import ru.my.traty.data.AppSettings

/** События распознавания; могут приходить с любого потока. */
interface SpeechListener {
    /** Текст, распознанный к этому моменту (может быть неполным). */
    fun onPartial(text: String)

    /** Громкость 0..1 — для анимации кнопки. */
    fun onLevel(level: Float)

    /** Запись закончена: полный текст (может быть пустым). */
    fun onFinal(text: String)

    fun onError(error: Throwable)
}

/** Движок распознавания речи. Один экземпляр — одна запись. */
interface SpeechEngine {
    fun start(listener: SpeechListener)

    /** Закончить запись и выдать [SpeechListener.onFinal] с тем, что услышано. */
    fun stop()

    /** Прервать запись без результата. */
    fun cancel()

    fun release()
}

object SpeechEngines {
    /** Системное распознавание; встроенный GigaAM подключается на этапе «GigaAM» плана. */
    fun create(context: Context, @Suppress("UNUSED_PARAMETER") settings: AppSettings): SpeechEngine =
        SystemSpeechEngine(context.applicationContext)
}
