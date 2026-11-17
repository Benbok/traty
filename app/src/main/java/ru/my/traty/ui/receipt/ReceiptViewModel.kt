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
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptText
import ru.my.traty.ai.receipt.isRetryable
import ru.my.traty.capture.ReceiptImage
import ru.my.traty.capture.ReceiptParts
import ru.my.traty.capture.ReceiptReview
import ru.my.traty.capture.ReceiptStep
import ru.my.traty.data.canScanReceipts
import java.io.File

/** Что показывает экран чека. [Idle] — экран закрыт. */
sealed interface ReceiptUiState {
    data object Idle : ReceiptUiState
    data class Photos(val texts: List<String>) : ReceiptUiState
    data class Working(val step: ReceiptStep) : ReceiptUiState
    data class Review(val review: ReceiptReview) : ReceiptUiState
    data class Failed(val message: String, val canRetry: Boolean = false) : ReceiptUiState
}

/** Части чека остаются в памяти до общей проверки. Сохраняется одна трата по кнопке пользователя. */
class ReceiptViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    val canScan: StateFlow<Boolean> = c.settings.flow.map { it.canScanReceipts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var state by mutableStateOf<ReceiptUiState>(ReceiptUiState.Idle)
        private set

    private var job: Job? = null
    private var texts = emptyList<String>()
    // Только неудачно прочитанный снимок. Успешные фото сразу освобождаются, остаётся текст.
    private var lastJpeg: ByteArray? = null
    private var retryParsing = false
    private val photoFile: File get() = File(File(getApplication<Application>().cacheDir, "receipts"), "receipt.jpg")

    fun cameraUri(): Uri {
        val app = getApplication<Application>()
        val file = photoFile.also { it.parentFile?.mkdirs() }
        return FileProvider.getUriForFile(app, "${app.packageName}.receipts", file)
    }

    /** Новый снимок добавляется к текущему чеку; ошибка не теряет уже прочитанные части. */
    fun scan(uri: Uri) {
        if (state is ReceiptUiState.Working || texts.size >= ReceiptParts.MAX_PHOTOS) return
        lastJpeg = null
        readPhoto {
            try {
                ReceiptImage.prepare(getApplication(), uri).also { lastJpeg = it }
            } finally {
                withContext(Dispatchers.IO + NonCancellable) { runCatching { photoFile.delete() } }
            }
        }
    }

    private fun readPhoto(photo: suspend () -> ByteArray) {
        retryParsing = false
        job?.cancel()
        state = ReceiptUiState.Working(ReceiptStep.READING)
        job = viewModelScope.launch {
            try {
                val text = c.receipts.recognize(photo())
                if (ReceiptText.clean(text).length < 10) {
                    throw ReceiptException("На фото не нашлось текста чека. Переснимите ближе и при хорошем свете")
                }
                texts = texts + text
                lastJpeg = null
                state = ReceiptUiState.Photos(texts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e), lastJpeg != null && ReceiptErrors.isRetryable(e))
            }
        }
    }

    fun removePhoto(index: Int) {
        if (state !is ReceiptUiState.Photos) return
        texts = texts.filterIndexed { i, _ -> i != index }
        state = ReceiptUiState.Photos(texts)
    }

    fun parsePhotos() {
        if (state is ReceiptUiState.Working || texts.isEmpty()) return
        retryParsing = true
        state = ReceiptUiState.Working(ReceiptStep.PARSING)
        job = viewModelScope.launch {
            try {
                val review = c.receipts.parseParts(texts)
                state = ReceiptUiState.Review(review)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e), ReceiptErrors.isRetryable(e))
            }
        }
    }

    /** Ошибка разбора повторяет только DeepSeek, ошибка чтения — только последний снимок. */
    fun retry() {
        if (state !is ReceiptUiState.Failed) return
        if (retryParsing) parsePhotos() else lastJpeg?.let { jpeg -> readPhoto { jpeg } }
    }

    fun returnToPhotos() {
        if (state is ReceiptUiState.Working) return
        lastJpeg = null
        retryParsing = false
        state = ReceiptUiState.Photos(texts)
    }

    fun photoNotTaken() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { photoFile.delete() } }
    }

    fun close() {
        job?.cancel()
        job = null
        lastJpeg = null
        texts = emptyList()
        retryParsing = false
        state = ReceiptUiState.Idle
    }
}
