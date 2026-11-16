package ru.my.traty.speech

import android.content.Context
import ru.my.traty.TratyApp
import ru.my.traty.data.AppSettings
import ru.my.traty.data.VoiceEngine

/** События распознавания; могут приходить с любого потока. */
interface SpeechListener {
    /** Текст, распознанный к этому моменту (может быть неполным). */
    fun onPartial(text: String)

    /** Громкость 0..1 — для анимации кнопки. */
    fun onLevel(level: Float)

    /**
     * Микрофон уже не слушает (пауза или конец речи), движок дораспознаёт хвост и скоро вызовет [onFinal].
     * Для длинной фразы это может занять несколько секунд.
     */
    fun onRecognizing() {}

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
    /** GigaAM — если выбран в настройках и модель скачана; иначе системное распознавание. */
    fun create(context: Context, settings: AppSettings): SpeechEngine {
        val container = (context.applicationContext as TratyApp).container
        // Файл модели могли положить, пока приложение уже было запущено: проверяем заново (это дешёво)
        container.models.refresh()
        return if (settings.voiceEngine == VoiceEngine.GIGAAM && container.gigaAm.isReady()) {
            GigaAmEngine(container.gigaAm)
        } else {
            SystemSpeechEngine(context.applicationContext)
        }
    }
}
