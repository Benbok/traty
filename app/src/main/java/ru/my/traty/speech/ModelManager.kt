package ru.my.traty.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class ModelFile(val name: String, val url: String, val sizeBytes: Long, val sha256: String)

/** Крупные файлы модели, которые приложение качает само. Словарь и модель детектора пауз лежат в assets. */
data class ModelManifest(val id: String, val files: List<ModelFile>)

/** GigaAM v3 e2e CTC (int8, со знаками препинания): сторонняя ONNX-выгрузка protocolvoice, MIT. */
val GIGAAM_MANIFEST = ModelManifest(
    id = "gigaam-v3-e2e-ctc-int8",
    files = listOf(
        ModelFile(
            name = "gigaam_v3_e2e_ctc_int8.onnx",
            url = "https://huggingface.co/protocolvoice/asr-models/resolve/main/gigaam_v3_e2e_ctc_int8.onnx",
            sizeBytes = 319_869_121L,
            sha256 = "0aacb41f70f0f5aaac4b45dd430337b9e16b180f22c72af04db8516e7609c3c0",
        ),
    ),
)

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val progress: Float) : ModelState
    data class Ready(val dir: File) : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Состояние и скачивание модели. [dirs] — где искать файлы; первая папка — куда скачивать
 * (остальные позволяют положить модель вручную, например в Android/data/…/files).
 * «Готово» — когда все файлы манифеста лежат в одной папке с правильным размером;
 * хэш SHA-256 проверяется при скачивании.
 */
class ModelManager(
    private val dirs: List<File>,
    private val manifest: ModelManifest = GIGAAM_MANIFEST,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val _state = MutableStateFlow<ModelState>(scan())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value !is ModelState.Downloading) _state.value = scan()
    }

    private fun scan(): ModelState {
        val ready = dirs.firstOrNull { d -> manifest.files.all { f -> File(d, f.name).let { it.isFile && it.length() == f.sizeBytes } } }
        return if (ready != null) ModelState.Ready(ready) else ModelState.Missing
    }

    suspend fun download() {
        if (_state.value is ModelState.Downloading || _state.value is ModelState.Ready) return
        val target = dirs.first()
        _state.value = ModelState.Downloading(0f)
        val total = manifest.files.sumOf { it.sizeBytes }
        var done = 0L
        try {
            withContext(Dispatchers.IO) {
                target.mkdirs()
                for (f in manifest.files) {
                    downloadFile(f, target) { n ->
                        done += n
                        _state.value = ModelState.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            _state.value = scan()
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun downloadFile(f: ModelFile, dir: File, onBytes: (Int) -> Unit) {
        val part = File(dir, f.name + ".part")
        val out = File(dir, f.name)
        try {
            http.newCall(Request.Builder().url(f.url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("Пустой ответ сервера")
                val digest = MessageDigest.getInstance("SHA-256")
                part.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            onBytes(n)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(f.sha256, ignoreCase = true)) throw IOException("Контрольная сумма не совпала — файл повреждён")
                if (part.length() != f.sizeBytes) throw IOException("Неверный размер файла")
            }
            out.delete()
            if (!part.renameTo(out)) throw IOException("Не удалось сохранить файл модели")
        } finally {
            part.delete()
        }
    }

    /** Удаляет файлы модели из папки загрузки (вручную положенные в другие папки не трогает). */
    fun delete() {
        if (_state.value is ModelState.Downloading) return
        manifest.files.forEach { File(dirs.first(), it.name).delete() }
        _state.value = scan()
    }
}
