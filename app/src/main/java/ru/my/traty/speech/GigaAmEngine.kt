package ru.my.traty.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import kotlin.math.sqrt

/**
 * Распознавание речи GigaAM на телефоне. Микрофон 16 кГц → детектор пауз (Silero) → фразы по паузам
 * распознаются моделью → текст копится; запись заканчивается по паузе ~1,5 с (см. [VadSegmenter]).
 * Нужен доступ к микрофону (его запрашивает экран). Один экземпляр — одна запись.
 */
class GigaAmEngine(private val runtime: GigaAmRuntime) : SpeechEngine {
    @Volatile private var stopRequested = false
    @Volatile private var cancelled = false

    override fun start(listener: SpeechListener) {
        runtime.preload()
        Thread({ run(listener) }, "gigaam-record").start()
    }

    override fun stop() { stopRequested = true }

    override fun cancel() {
        cancelled = true
        stopRequested = true
    }

    override fun release() {
        cancelled = true
        stopRequested = true
    }

    @SuppressLint("MissingPermission")
    private fun run(listener: SpeechListener) {
        val rate = GigaAmRuntime.SAMPLE_RATE
        val window = GigaAmRuntime.VAD_WINDOW
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Буфер на 4 секунды: пока модель загружается или распознаёт, звук не теряется
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, rate * 2 * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            listener.onError(IllegalStateException("Не удалось открыть микрофон"))
            return
        }
        val vad = try {
            runtime.newVad()
        } catch (e: Throwable) {
            record.release()
            Log.e(TAG, "vad init failed", e)
            listener.onError(IllegalStateException("Не удалось запустить распознавание речи"))
            return
        }
        val texts = mutableListOf<String>()
        try {
            record.startRecording()
            val shorts = ShortArray(window)
            val floats = FloatArray(window)
            val segmenter = VadSegmenter()
            val frameMs = window * 1000 / rate
            while (!stopRequested) {
                var read = 0
                while (read < window && !stopRequested) {
                    val n = record.read(shorts, read, window - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < window) break
                var sum = 0.0
                for (i in 0 until window) {
                    floats[i] = shorts[i] / 32768f
                    sum += floats[i] * floats[i]
                }
                listener.onLevel((sqrt(sum / window).toFloat() * 8f).coerceIn(0f, 1f))
                vad.acceptWaveform(floats)
                drain(vad, texts, listener)
                if (segmenter.onFrame(vad.isSpeechDetected(), frameMs) == VadAction.STOP) break
            }
            if (!cancelled) {
                vad.flush()
                drain(vad, texts, listener)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "recording failed", e)
            if (!cancelled) listener.onError(IllegalStateException(e.message ?: "Ошибка распознавания речи"))
            return
        } finally {
            runCatching { record.stop() }
            record.release()
            vad.release()
            runtime.scheduleUnload()
        }
        if (!cancelled) listener.onFinal(texts.joinToString(" "))
    }

    private fun drain(vad: Vad, texts: MutableList<String>, listener: SpeechListener) {
        while (!vad.empty() && !cancelled) {
            val segment = vad.front()
            vad.pop()
            val text = runtime.recognize(segment.samples)
            if (text.isNotBlank()) {
                texts += text
                listener.onPartial(texts.joinToString(" "))
            }
        }
    }

    private companion object {
        const val TAG = "GigaAm"
    }
}
