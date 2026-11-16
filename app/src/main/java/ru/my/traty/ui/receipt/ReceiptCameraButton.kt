package ru.my.traty.ui.receipt

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Кнопка «Чек» рядом с микрофоном на «Тратах». Видна, только когда в настройках ИИ заполнены
 * API-ключ и ID каталога Яндекса. Нажатие открывает меню: снять чек камерой или взять фото из галереи.
 */
@Composable
fun ReceiptCameraButton(
    enabled: Boolean,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ReceiptViewModel = viewModel(),
) {
    val canScan by vm.canScan.collectAsStateWithLifecycle()
    if (!canScan) return
    var menu by remember { mutableStateOf(false) }
    // Адрес файла для камеры переживает поворот экрана, пока открыта камера
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingUri
        pendingUri = null
        if (taken && uri != null) vm.scan(uri) else vm.photoNotTaken()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(uri)
    }
    Box(modifier) {
        TratyIconButton(
            icon = Icons.Filled.PhotoCamera,
            contentDescription = "Чек по фото",
            onClick = { menu = true },
            enabled = enabled,
            containerColor = TratyTheme.colors.surfaceContainer,
            tint = TratyTheme.colors.primary,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Сфотографировать чек") },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                onClick = {
                    menu = false
                    val uri = vm.cameraUri()
                    pendingUri = uri
                    try {
                        camera.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        pendingUri = null
                        onError("На телефоне не нашлось приложения камеры")
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Выбрать фото чека") },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                onClick = {
                    menu = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}
