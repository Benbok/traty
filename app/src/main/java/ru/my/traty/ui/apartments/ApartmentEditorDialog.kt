package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import ru.my.traty.data.apartments.Apartment
import ru.my.traty.ui.designsystem.components.*

@Composable
internal fun ApartmentEditorScreen(value: Apartment, vm: ApartmentsViewModel, onDismiss: () -> Unit) {
    val isNew = value.id == 0L
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var address by rememberSaveable(value.id) { mutableStateOf(value.address.orEmpty()) }
    var archived by rememberSaveable(value.id) { mutableStateOf(value.archived) }
    HousingEditorPage(
        title = if (isNew) "Новая квартира" else value.name,
        subtitle = if (isNew) "Создание квартиры" else "Редактирование квартиры",
        isNew = isNew,
        busy = vm.busy,
        canSave = name.isNotBlank(),
        error = vm.message,
        onDismiss = onDismiss,
        onSave = {
            vm.save(
                value.copy(name = name.trim(), address = address.trim().ifBlank { null }, archived = archived),
                onDismiss,
            )
        },
    ) {
        HousingEditorPreview(
            emoji = "🏠",
            title = name.ifBlank { "Новая квартира" },
            subtitle = address.ifBlank { "Счётчики и показания в одном месте" },
        )
        TratyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                TratyInlineRow("Название", name, placeholder = "Например, Квартира на Лесной", enabled = !vm.busy) { name = it }
                TratyRowDivider()
                TratyInlineRow("Адрес", address, placeholder = "Необязательно", enabled = !vm.busy) { address = it }
                if (!isNew) {
                    TratyRowDivider()
                    TratySwitchRow(
                        title = "В архиве",
                        subtitle = "Счётчики и история показаний сохранятся",
                        checked = archived,
                        enabled = !vm.busy,
                    ) { archived = it }
                }
            }
        }
    }
}
