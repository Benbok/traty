package ru.my.traty.speech

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Держит загруженную модель GigaAM между записями: загрузка ~320 МБ занимает секунды, поэтому модель
 * остаётся в памяти [KEEP_WARM_MS] после последней записи и потом выгружается.
 */
class GigaAmRuntime(private val context: Context, private val models: ModelManager) {
    private val lock = Any()
    private var recognizer: OfflineRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val unload = Runnable { unloadNow() }
    private val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Можно ли распознавать: модель скачана или положена вручную. */
    fun isReady(): Boolean = models.state.value is ModelState.Ready

    /** Начать загрузку модели заранее — пока человек говорит, она успеет загрузиться. */
    fun preload() {
        handler.removeCallbacks(unload)
        Thread({ runCatching { synchronized(lock) { ensureLoaded() } }.onFailure { Log.w(TAG, "preload failed", it) } }, "gigaam-load").start()
    }

    fun recognize(samples: FloatArray): String = synchronized(lock) {
        val r = ensureLoaded()
        val started = System.nanoTime()
        val stream = r.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            val text = r.getResult(stream).text.trim()
            if (debuggable) Log.d(TAG, "segment %.1fs -> \"%s\" in %d ms".format(samples.size / SAMPLE_RATE.toFloat(), text, (System.nanoTime() - started) / 1_000_000))
            text
        } finally {
            stream.release()
        }
    }

    fun newVad(): Vad = Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
                windowSize = VAD_WINDOW,
                maxSpeechDuration = 30f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
        ),
    )

    /** Вызывается после записи: выгрузить модель, если ближайшую минуту не понадобится. */
    fun scheduleUnload() {
        handler.removeCallbacks(unload)
        handler.postDelayed(unload, KEEP_WARM_MS)
    }

    private fun unloadNow() {
        synchronized(lock) {
            recognizer?.release()
            recognizer = null
        }
    }

    private fun ensureLoaded(): OfflineRecognizer {
        recognizer?.let { return it }
        val dir = (models.state.value as? ModelState.Ready)?.dir ?: error("Модель GigaAM не скачана")
        val started = System.nanoTime()
        val r = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = File(dir, GIGAAM_MANIFEST.files.first().name).absolutePath),
                    tokens = prepareTokens().absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                ),
            ),
        )
        recognizer = r
        if (debuggable) Log.d(TAG, "model loaded in ${(System.nanoTime() - started) / 1_000_000} ms")
        return r
    }

    /** Словарь лежит в assets, а нативной библиотеке нужен обычный файл. */
    private fun prepareTokens(): File {
        val out = File(context.filesDir, "models/gigaam_tokens.txt")
        out.parentFile?.mkdirs()
        context.assets.open(TOKENS_ASSET).use { input ->
            val bytes = input.readBytes()
            if (!out.isFile || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val VAD_WINDOW = 512
        private const val FEATURE_DIM = 64
        private const val KEEP_WARM_MS = 60_000L
        private const val TOKENS_ASSET = "gigaam_tokens.txt"
        private const val TAG = "GigaAm"
    }
}
