package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import ru.my.traty.data.apartments.Apartment

@Composable
internal fun ApartmentEditorDialog(value: Apartment, vm: ApartmentsViewModel, onDismiss: () -> Unit) {
    var name by remember(value.id) { mutableStateOf(value.name) }
    var address by remember(value.id) { mutableStateOf(value.address.orEmpty()) }
    var archived by remember(value.id) { mutableStateOf(value.archived) }
    HousingDialog("Квартира", vm.message, vm.busy, onDismiss, { vm.save(value.copy(name = name, address = address.trim().ifBlank { null }, archived = archived), onDismiss) }) {
        HousingField(name, "Название") { name = it }
        HousingField(address, "Адрес — необязательно") { address = it }
        if (value.id != 0L) Row { Checkbox(archived, { archived = it }); Text("В архиве, история сохранится") }
    }
}
