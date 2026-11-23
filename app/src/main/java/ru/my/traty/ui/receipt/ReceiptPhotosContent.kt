package ru.my.traty.ui.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ReceiptPhotosContent(
    photos: List<ReceiptPhoto>,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onParse: () -> Unit,
    photoButton: @Composable ((String) -> Unit) -> Unit,
    retakeButton: @Composable (Long, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sp = TratyTheme.spacing
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ReceiptPhoto?>(null) }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(sp.lg),
            verticalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            item {
                Text("Снимайте чек сверху вниз с перекрытием в 2–3 полные строки. Захватите итог на последнем кадре.")
            }
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                PhotoCard(
                    photo, index, photos.lastIndex,
                    onPreview = { preview = photo },
                    onMove = { onMove(index, it) },
                    onRemove = { onRemove(index) },
                    retake = { retakeButton(photo.id) { error = it } },
                )
            }
            item { photoButton { error = it } }
            error?.let { message -> item { Text(message, color = TratyTheme.colors.expense) } }
            if (photos.isNotEmpty()) {
                item {
                    Text(
                        "Для распознавания фото отправляются в DeepSeek.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
        TratyButton(
            onClick = onParse,
            enabled = photos.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = sp.lg, vertical = sp.sm),
        ) { Text("Распознать чек") }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}

@Composable
private fun PhotoCard(
    photo: ReceiptPhoto,
    index: Int,
    lastIndex: Int,
    onPreview: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    retake: @Composable () -> Unit,
) {
    val sp = TratyTheme.spacing
    TratyCard(Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surfaceContainer) {
        Column(Modifier.padding(sp.md), verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Text("Снимок ${index + 1}", style = TratyTheme.typography.titleSmall)
            Image(
                bitmap = photo.thumbnail.asImageBitmap(),
                contentDescription = "Миниатюра снимка ${index + 1}",
                modifier = Modifier.fillMaxWidth().heightIn(max = sp.xl * 5),
                contentScale = ContentScale.Fit,
            )
            Row {
                TratyTextButton(onClick = onPreview) { Text("Открыть") }
                retake()
                TratyTextButton(onClick = onRemove) { Text("Удалить") }
            }
            if (lastIndex > 0) {
                Row {
                    TratyTextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text("Выше") }
                    TratyTextButton(onClick = { onMove(1) }, enabled = index < lastIndex) { Text("Ниже") }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(photo: ReceiptPhoto, onClose: () -> Unit) {
    val bitmap by produceState<Bitmap>(photo.thumbnail, photo) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
        } ?: photo.thumbnail
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Снимок чека") },
        text = {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Фото чека",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        },
        confirmButton = { TratyTextButton(onClick = onClose) { Text("Готово") } },
    )
}
