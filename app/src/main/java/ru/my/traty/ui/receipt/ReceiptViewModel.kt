package ru.my.traty.ui.receipt

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import ru.my.traty.ai.receipt.isRetryable
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.EntryOrigin
import ru.my.traty.capture.EntryRow
import ru.my.traty.capture.ReceiptImage
import ru.my.traty.capture.ReceiptParts
import ru.my.traty.capture.ReceiptReview
import ru.my.traty.capture.SaveResult
import ru.my.traty.data.canScanReceipts
import ru.my.traty.data.items.ItemNames
import java.io.File

class ReceiptPhoto(val id: Long, val jpeg: ByteArray, val thumbnail: Bitmap)

sealed interface ReceiptUiState {
    data object Idle : ReceiptUiState
    data class Photos(val photos: List<ReceiptPhoto>) : ReceiptUiState
    data class Working(val title: String, val canCancel: Boolean = true) : ReceiptUiState
    data class Review(
        val review: ReceiptReview,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : ReceiptUiState
    data class Failed(val message: String, val canRetry: Boolean = false) : ReceiptUiState
}

class ReceiptViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    val canScan: StateFlow<Boolean> = c.settings.flow.map { it.canScanReceipts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var state by mutableStateOf<ReceiptUiState>(ReceiptUiState.Idle)
        private set

    private var job: Job? = null
    private var photos = emptyList<ReceiptPhoto>()
    private var photoId = 0L
    private val photoFile: File get() = File(File(getApplication<Application>().cacheDir, "receipts"), "receipt.jpg")

    fun cameraUri(): Uri {
        val app = getApplication<Application>()
        val file = photoFile.also { it.parentFile?.mkdirs() }
        return FileProvider.getUriForFile(app, "${app.packageName}.receipts", file)
    }

    fun scan(uri: Uri, replaceId: Long? = null) {
        val replaceIndex = if (replaceId == null) null else photos.indexOfFirst { it.id == replaceId }
        if (state is ReceiptUiState.Working) return
        if (replaceIndex == null && photos.size >= ReceiptParts.MAX_PHOTOS) return
        if (replaceIndex != null && replaceIndex !in photos.indices) {
            photoNotTaken()
            return
        }
        state = ReceiptUiState.Working("Подготавливаем фото…", canCancel = false)
        job = viewModelScope.launch {
            try {
                val photo = try {
                    preparePhoto(uri, ++photoId)
                } finally {
                    withContext(Dispatchers.IO + NonCancellable) { photoFile.delete() }
                }
                if (photos.withIndex().any { it.index != replaceIndex && it.value.jpeg.contentEquals(photo.jpeg) }) {
                    throw ReceiptException("Этот снимок уже добавлен")
                }
                photos = if (replaceIndex == null) photos + photo else photos.toMutableList().apply { set(replaceIndex, photo) }
                returnToPhotos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e))
            }
        }
    }

    private suspend fun preparePhoto(uri: Uri, id: Long): ReceiptPhoto = withContext(Dispatchers.IO) {
        val jpeg = ReceiptImage.prepare(getApplication(), uri)
        val thumbnail = BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 8 },
        ) ?: throw ReceiptException("Фото не открылось, попробуйте ещё раз")
        ReceiptPhoto(id, jpeg, thumbnail)
    }

    fun removePhoto(index: Int) {
        if (state !is ReceiptUiState.Photos || index !in photos.indices) return
        photos = photos.filterIndexed { i, _ -> i != index }
        returnToPhotos()
    }

    fun movePhoto(index: Int, offset: Int) {
        val destination = index + offset
        if (state !is ReceiptUiState.Photos || index !in photos.indices || destination !in photos.indices) return
        photos = photos.toMutableList().apply { add(destination, removeAt(index)) }
        returnToPhotos()
    }

    fun parsePhotos() {
        if (state !is ReceiptUiState.Photos && state !is ReceiptUiState.Failed) return
        if (photos.isEmpty()) return
        state = ReceiptUiState.Working("Распознаём чек…")
        val frames = photos.map { it.jpeg }
        job = viewModelScope.launch {
            try {
                val review = c.receipts.scanPhotos(frames)
                state = ReceiptUiState.Review(review)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = ReceiptUiState.Failed(ReceiptErrors.text(e), ReceiptErrors.isRetryable(e))
            }
        }
    }

    fun retry() {
        if ((state as? ReceiptUiState.Failed)?.canRetry == true) parsePhotos()
    }

    fun cancelRecognition() {
        if ((state as? ReceiptUiState.Working)?.canCancel != true) return
        job?.cancel()
        job = null
        returnToPhotos()
    }

    fun returnToPhotos() {
        if ((state as? ReceiptUiState.Review)?.saving == true) return
        state = ReceiptUiState.Photos(photos)
    }

    fun fillManually() {
        if (state !is ReceiptUiState.Failed) return
        val draft = EntryDraft(listOf(EntryRow.empty()), rawText = "", origin = EntryOrigin.MANUAL)
        state = ReceiptUiState.Review(ReceiptReview(draft, emptyList()))
    }

    fun editReview(row: EntryRow) {
        val current = state as? ReceiptUiState.Review ?: return
        if (current.saving) return
        val draft = current.review.draft.copy(rows = listOf(row))
        state = current.copy(review = current.review.copy(draft = draft), saveError = null)
    }

    fun submit(onSaved: (SaveResult) -> Unit) {
        val current = state as? ReceiptUiState.Review ?: return
        if (current.saving) return
        val draft = current.review.draft
        val row = draft.rows.single()
        if (row.kopecks() == null || row.dateText.isBlank() || row.items.any { ItemNames.normalize(it.name) == null }) return
        state = current.copy(saving = true, saveError = null)
        viewModelScope.launch {
            val result = try {
                c.quickEntry.save(draft).takeIf { it.saved.count == 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result == null) {
                state = current.copy(saveError = "Не удалось сохранить чек. Попробуйте ещё раз")
            } else {
                close()
                onSaved(result)
            }
        }
    }

    fun photoNotTaken() {
        viewModelScope.launch(Dispatchers.IO) { photoFile.delete() }
    }

    fun close() {
        job?.cancel()
        job = null
        photos = emptyList()
        state = ReceiptUiState.Idle
    }
}
