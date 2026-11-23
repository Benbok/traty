package ru.my.traty.ui.receipt

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import ru.my.traty.TratyApp
import ru.my.traty.data.canScanReceipts
import ru.my.traty.ui.designsystem.components.TratyTextButton
import androidx.compose.material3.Text

const val RECEIPT_CAMERA_ACTION = "ru.my.traty.action.PHOTOGRAPH_RECEIPT"

/** Вход с рабочего стола; разбор и проверку показывает обычный ReceiptOverlay в том же Activity. */
@Composable
fun ReceiptCameraEntry(
    requested: Boolean,
    onHandled: () -> Unit,
    vm: ReceiptViewModel = viewModel(),
) {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingUri
        pendingUri = null
        if (taken && uri != null) vm.scan(uri) else vm.photoNotTaken()
    }
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        try {
            // Ждём реального чтения настроек, а не начального false у StateFlow.
            val settings = (context.applicationContext as TratyApp).container.settings.current()
            onHandled()
            when {
                !settings.canScanReceipts ->
                    error = "Для распознавания чеков сохраните ключ DeepSeek в «Настройки → ИИ»."
                pendingUri != null || vm.state != ReceiptUiState.Idle ->
                    error = "Сначала завершите или закройте текущий чек."
                else -> {
                    val uri = vm.cameraUri()
                    pendingUri = uri
                    camera.launch(uri)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ActivityNotFoundException) {
            pendingUri = null
            vm.photoNotTaken()
            error = "На телефоне не нашлось приложения камеры."
        } catch (_: Exception) {
            onHandled()
            pendingUri = null
            vm.photoNotTaken()
            error = "Не удалось открыть камеру. Попробуйте ещё раз."
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Фото чека") },
            text = { Text(message) },
            confirmButton = { TratyTextButton(onClick = { error = null }) { Text("Понятно") } },
        )
    }
}
