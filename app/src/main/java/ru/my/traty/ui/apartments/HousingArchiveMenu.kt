package ru.my.traty.ui.apartments

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.my.traty.data.apartments.Apartment
import ru.my.traty.data.apartments.Meter
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ApartmentActions(apartment: Apartment, vm: HousingViewModel, onEdit: () -> Unit) {
    HousingArchiveMenu(
        description = "Действия с квартирой ${apartment.name}",
        name = apartment.name,
        archived = apartment.archived,
        historyText = "Счётчики и история показаний сохранятся.",
        vm = vm,
        onEdit = onEdit,
        onChange = { archived, onSaved -> vm.setApartmentArchived(apartment.id, archived, onSaved) },
    )
}

@Composable
internal fun MeterActions(meter: Meter, vm: HousingViewModel, onEdit: () -> Unit) {
    HousingArchiveMenu(
        description = "Действия со счётчиком ${meter.name}",
        name = meter.name,
        archived = meter.archived,
        historyText = "История показаний и отметки о передаче сохранятся.",
        vm = vm,
        onEdit = onEdit,
        onChange = { archived, onSaved -> vm.setMeterArchived(meter.id, archived, onSaved) },
    )
}

@Composable
private fun HousingArchiveMenu(
    description: String,
    name: String,
    archived: Boolean,
    historyText: String,
    vm: HousingViewModel,
    onEdit: () -> Unit,
    onChange: (Boolean, () -> Unit) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    HousingMenu(
        description = description,
        onEdit = onEdit,
        archived = archived,
        onArchive = { confirming = true },
    )
    if (!confirming) return

    var attempted by remember { mutableStateOf(false) }
    val title = if (archived) "Вернуть из архива?" else "Перенести в архив?"
    val body = if (archived) "«$name» снова появится среди активных записей." else "«$name». $historyText"
    AlertDialog(
        onDismissRequest = { if (!vm.busy) confirming = false },
        title = { Text(title) },
        text = {
            Text(
                if (attempted) vm.message ?: body else body,
                color = if (attempted && vm.message != null) TratyTheme.colors.expense
                else TratyTheme.colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TratyTextButton(
                enabled = !vm.busy,
                onClick = {
                    attempted = true
                    onChange(!archived) { confirming = false }
                },
            ) { Text(if (archived) "Вернуть" else "В архив") }
        },
        dismissButton = {
            TratyTextButton(enabled = !vm.busy, onClick = { confirming = false }) { Text("Отмена") }
        },
    )
}
