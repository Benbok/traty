package ru.my.traty.ui.receipt

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.tratyPressFeedback
import ru.my.traty.ui.designsystem.components.TratyTonalButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Вход в добавление чека. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCameraButton(
    enabled: Boolean,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ReceiptViewModel = viewModel(),
    compact: Boolean = true,
) {
    val canScan by vm.canScan.collectAsStateWithLifecycle()
    if (!canScan) return
    var showSources by rememberSaveable { mutableStateOf(false) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingUri
        pendingUri = null
        if (taken && uri != null) vm.scan(uri) else vm.photoNotTaken()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(uri)
    }
    val takePhoto: () -> Unit = {
        showSources = false
        try {
            val uri = vm.cameraUri()
            pendingUri = uri
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingUri = null
            vm.photoNotTaken()
            onError("На телефоне не нашлось приложения камеры")
        }
    }
    val pickPhoto: () -> Unit = {
        showSources = false
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            onError("Не удалось открыть выбор фото")
        }
    }
    if (compact) {
        TratyTonalButton(
            onClick = { showSources = true },
            enabled = enabled,
            modifier = modifier,
        ) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(20.dp), tint = TratyTheme.colors.primary)
            Spacer(Modifier.width(TratyTheme.spacing.xs))
            Text("Чек", color = TratyTheme.colors.primary)
        }
    } else {
        // Прямые действия: следующая часть того же чека без повторного открытия панели.
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            TratyTonalButton(onClick = takePhoto, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp))
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text("Добавить фото")
            }
            TratyOutlinedButton(onClick = pickPhoto, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(20.dp))
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text("Из галереи")
            }
        }
    }
    if (showSources && enabled) {
        ModalBottomSheet(
            onDismissRequest = { showSources = false },
            containerColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = TratyTheme.spacing.lg)
                    .padding(bottom = TratyTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                Text("Добавить чек", style = TratyTheme.typography.titleMedium)
                ReceiptSource(
                    icon = Icons.Filled.PhotoCamera,
                    title = "Сфотографировать",
                    subtitle = "Бумажный чек целиком или по частям",
                    highlighted = true,
                    onClick = takePhoto,
                )
                ReceiptSource(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Выбрать фото",
                    subtitle = "Готовый снимок из галереи",
                    onClick = pickPhoto,
                )
                Text(
                    "Длинный чек можно добавить несколькими снимками",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiptSource(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.medium
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tratyPressFeedback(interactionSource, shape),
        shape = shape,
        interactionSource = interactionSource,
        color = if (highlighted) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
        contentColor = if (highlighted) TratyTheme.colors.onPrimaryContainer else TratyTheme.colors.onSurface,
    ) {
        Row(
            Modifier.heightIn(min = 80.dp).padding(TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Icon(icon, null, Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                Text(title, style = TratyTheme.typography.titleSmall)
                Text(subtitle, style = TratyTheme.typography.bodySmall)
            }
        }
    }
}
