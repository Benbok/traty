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
    data class Failed(val message: String) : ReceiptUiState
}

/** Фото чека: съёмка, разбор через Яндекс и экран проверки. Сохраняет трату экран «Траты» после проверки. */
class ReceiptViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    /** Кнопка камеры видна, только когда заполнены API-ключ и ID каталога Яндекса. */
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

    /** [uri] — фото с камеры ([cameraUri]) или из галереи. */
    fun scan(uri: Uri) {
        if (state is ReceiptUiState.Working) return
        job?.cancel()
        state = ReceiptUiState.Working(ReceiptStep.READING)
        job = viewModelScope.launch {
            try {
                val jpeg = ReceiptImage.prepare(getApplication(), uri)
                val review = c.receipts.scan(jpeg) { step -> state = ReceiptUiState.Working(step) }
                state = ReceiptUiState.Review(review)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e))
            } finally {
                // Фото чека на телефоне не храним: оно нужно только на время распознавания
                withContext(Dispatchers.IO + NonCancellable) { runCatching { photoFile.delete() } }
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
        state = ReceiptUiState.Idle
    }
}
