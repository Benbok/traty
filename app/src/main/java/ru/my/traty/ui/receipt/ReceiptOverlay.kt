package ru.my.traty.ui.receipt

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.capture.SaveResult
import ru.my.traty.capture.ReceiptParts
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import androidx.compose.material3.Text

/** Экран чека поверх приложения. Сообщает о сохранении после записи покупки. */
@Composable
fun ReceiptOverlay(
    categories: List<CategoryWithSubs>,
    onSaved: (SaveResult) -> Unit,
    vm: ReceiptViewModel = viewModel(),
) {
    val retake = rememberRetakeReceiptPhoto(vm)
    val state = vm.state
    if (state == ReceiptUiState.Idle) return
    Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
        ReceiptReviewScreen(
            state = state,
            categories = categories,
            onClose = vm::close,
            onRetry = vm::retry,
            onParse = vm::parsePhotos,
            onCancelRecognition = vm::cancelRecognition,
            onFillManually = vm::fillManually,
            onMovePhoto = vm::movePhoto,
            onRemovePhoto = vm::removePhoto,
            onReturnToPhotos = vm::returnToPhotos,
            photoButton = { onError ->
                ReceiptCameraButton(
                    enabled = state is ReceiptUiState.Photos && state.photos.size < ReceiptParts.MAX_PHOTOS,
                    onError = onError,
                    vm = vm,
                    compact = false,
                )
            },
            retakeButton = { id, onError ->
                TratyTextButton(onClick = { retake(id, onError) }) { Text("Переснять") }
            },
            onEdit = vm::editReview,
            onSave = { vm.submit(onSaved) },
        )
    }
}

@Composable
private fun rememberRetakeReceiptPhoto(vm: ReceiptViewModel): (Long, (String) -> Unit) -> Unit {
    var target by rememberSaveable { mutableStateOf<Long?>(null) }
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val id = target
        val photo = uri
        target = null
        uri = null
        if (taken && id != null && photo != null) vm.scan(photo, id) else vm.photoNotTaken()
    }
    return { id, onError ->
        if (target == null) {
            try {
                val photo = vm.cameraUri()
                target = id
                uri = photo
                camera.launch(photo)
            } catch (_: ActivityNotFoundException) {
                target = null
                uri = null
                vm.photoNotTaken()
                onError("На телефоне не нашлось приложения камеры")
            }
        }
    }
}

