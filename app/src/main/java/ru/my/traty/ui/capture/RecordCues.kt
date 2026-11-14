package ru.my.traty.ui.capture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Звуки начала и конца записи. */
interface RecordCues {
    /** Сыграть сигнал начала; [onDone] — когда он отзвучал (после этого можно включать микрофон, иначе он запишет сигнал). */
    fun playStart(onDone: () -> Unit)

    /** Сыграть сигнал окончания записи. */
    fun playEnd()

    /** Без звука: [playStart] сразу вызывает [onDone]. */
    object None : RecordCues {
        override fun playStart(onDone: () -> Unit) = onDone()
        override fun playEnd() = Unit
    }
}

/**
 * Два мягких «колокольчика», синтезируются в коде (файлов со звуками нет): начало — две ноты вверх, конец — две вниз.
 * Громкость идёт по каналу «звуки интерфейса», то есть уважает беззвучный режим.
 */
class ChimeRecordCues : RecordCues {
    private val main = Handler(Looper.getMainLooper())

    override fun playStart(onDone: () -> Unit) {
        val ok = play(START_NOTES)
        // Если звук не удалось проиграть, не задерживаем запись
        main.postDelayed(onDone, if (ok) START_NOTES.sumOf { it.second } + TAIL_MS else 0L)
    }

    override fun playEnd() {
        play(END_NOTES)
    }

    private fun play(notes: List<Pair<Double, Long>>): Boolean = runCatching {
        val pcm = synth(notes)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) = t.release()
            override fun onPeriodicNotification(t: AudioTrack) = Unit
        }, main)
        track.play()
        true
    }.getOrDefault(false)

    private fun synth(notes: List<Pair<Double, Long>>): ShortArray {
        val out = ArrayList<Short>()
        for ((freq, ms) in notes) {
            val n = (SAMPLE_RATE * (ms + TAIL_MS) / 1000).toInt()
            for (i in 0 until n) {
                val t = i / SAMPLE_RATE.toDouble()
                val attack = minOf(1.0, i / (SAMPLE_RATE * 0.006)) // 6 мс нарастания, чтобы не щёлкало
                val decay = exp(-t * 14.0)                         // мягкое затухание
                val v = (sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t)) * attack * decay * VOLUME
                out.add((v * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        return out.toShortArray()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val VOLUME = 0.28
        const val TAIL_MS = 60L
        val START_NOTES = listOf(784.0 to 70L, 1046.5 to 90L) // G5 → C6
        val END_NOTES = listOf(1046.5 to 70L, 784.0 to 90L)   // C6 → G5
    }
}
