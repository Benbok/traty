package ru.my.traty.ui.receipt

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.ai.receipt.ReceiptErrors
import ru.my.traty.ai.receipt.isRetryable
import ru.my.traty.capture.ReceiptImage
import ru.my.traty.capture.ReceiptReview
import ru.my.traty.capture.ReceiptStep
import ru.my.traty.data.canScanReceipts
import java.io.File

/** Что показывает экран чека. [Idle] — экран закрыт. */
sealed interface ReceiptUiState {
    data object Idle : ReceiptUiState
    data class Working(val step: ReceiptStep) : ReceiptUiState
    data class Review(val review: ReceiptReview) : ReceiptUiState
    /** [canRetry] — фото (и, если успели, текст чека) ещё в памяти: «Повторить» продолжит без новой съёмки. */
    data class Failed(val message: String, val canRetry: Boolean = false) : ReceiptUiState
}

/** Фото чека: съёмка, чтение текста Яндексом, разбор DeepSeek и экран проверки. Сохраняет трату экран «Траты» после проверки. */
class ReceiptViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    /** Кнопка камеры видна, только когда заполнены ключ и каталог Яндекса и ключ DeepSeek. */
    val canScan: StateFlow<Boolean> = c.settings.flow.map { it.canScanReceipts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var state by mutableStateOf<ReceiptUiState>(ReceiptUiState.Idle)
        private set

    private var job: Job? = null

    private val photoFile: File get() = File(File(getApplication<Application>().cacheDir, "receipts"), "receipt.jpg")

    /** Куда камера запишет фото: файл в кэше, отданный камере через FileProvider. */
    fun cameraUri(): Uri {
        val app = getApplication<Application>()
        val file = photoFile.also { it.parentFile?.mkdirs() }
        return FileProvider.getUriForFile(app, "${app.packageName}.receipts", file)
    }

    /** Фото после сжатия и текст, который Яндекс уже прочитал: для «Повторить» после ошибки. Только в памяти. */
    private var lastJpeg: ByteArray? = null
    private var lastText: String? = null

    /** [uri] — фото с камеры ([cameraUri]) или из галереи. */
    fun scan(uri: Uri) {
        if (state is ReceiptUiState.Working) return
        lastJpeg = null
        lastText = null
        process {
            try {
                ReceiptImage.prepare(getApplication(), uri).also { lastJpeg = it }
            } finally {
                // Фото чека на телефоне не храним: после сжатия оно остаётся только в памяти до закрытия экрана
                withContext(Dispatchers.IO + NonCancellable) { runCatching { photoFile.delete() } }
            }
        }
    }

    /** Продолжить с шага, на котором была ошибка: прочитанный Яндексом текст повторно не отправляется. */
    fun retry() {
        val jpeg = lastJpeg ?: return
        if (state is ReceiptUiState.Working) return
        process { jpeg }
    }

    private fun process(photo: suspend () -> ByteArray) {
        job?.cancel()
        state = ReceiptUiState.Working(ReceiptStep.READING)
        job = viewModelScope.launch {
            try {
                val text = lastText ?: c.receipts.recognize(photo()) { step -> state = ReceiptUiState.Working(step) }
                    .also { lastText = it }
                state = ReceiptUiState.Review(c.receipts.parse(text) { step -> state = ReceiptUiState.Working(step) })
                lastJpeg = null
                lastText = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e), canRetry = lastJpeg != null && ReceiptErrors.isRetryable(e))
            }
        }
    }

    fun photoNotTaken() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { photoFile.delete() } }
    }

    /** Закрыть экран: разбор, если он идёт, останавливается. */
    fun close() {
        job?.cancel()
        job = null
        lastJpeg = null
        lastText = null
        state = ReceiptUiState.Idle
    }
}
