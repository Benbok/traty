package ru.my.traty.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val COPY_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", java.util.Locale("ru"))

/** «Текущие данные будут заменены данными из копии» и сколько записей в каждом разделе. */
@Composable
internal fun RestoreConfirmDialog(offer: RestoreOffer, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val made = Instant.ofEpochMilli(offer.backup.createdAt).atZone(ZoneId.systemDefault()).format(COPY_DATE)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Восстановить из копии?", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                Text(
                    "Текущие данные будут заменены данными из копии. Копия от $made, в ней:",
                    style = TratyTheme.typography.bodyMedium,
                )
                Text("Квартиры: сейчас ${offer.currentApartments}, в копии ${offer.backup.housing.apartments.size}. Показания: сейчас ${offer.currentReadings}, в копии ${offer.backup.housing.readings.size}. Текущая история будет удалена и заменена, даже если в копии её нет.", style = TratyTheme.typography.bodySmall)
                offer.sections.forEach { (title, count) -> SectionRow(title, count) }
                Text(
                    "Ключи ИИ в копии нет: после восстановления введите их заново в «Модели и API-ключи».",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TratyButton(onClick = onConfirm) { Text("Заменить") } },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SectionRow(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
        Text(count.toString(), style = TratyTheme.typography.bodySmall)
    }
}
