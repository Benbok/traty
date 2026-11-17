package ru.my.traty.ui.apartments

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton

@Composable
fun ApartmentsRoute() {
    var apartment by rememberSaveable { mutableStateOf<Long?>(null) }
    var meter by rememberSaveable { mutableStateOf<Long?>(null) }
    var resource by rememberSaveable { mutableStateOf<String?>(null) }
    val apartmentId = apartment
    val meterId = meter
    val resourceName = resource
    when {
        meterId != null -> MeterDetailScreen(meterId, { meter = null })
        apartmentId != null && resourceName != null -> ResourceDetailScreen(apartmentId, ResourceType.valueOf(resourceName), { resource = null })
        apartmentId != null -> ApartmentDetailScreen(apartmentId, { apartment = null }, { meter = it }, { resource = it.name })
        else -> ApartmentsScreen(onOpenApartment = { apartment = it })
    }
}

@Composable
fun ApartmentsScreen(onOpenApartment: (Long) -> Unit, vm: ApartmentsViewModel = viewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var archived by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Apartment?>(null) }
    HousingPage("Квартиры", vm.message, actions = { TratyTextButton(onClick = { editing = Apartment(name = "") }) { Text("Добавить") } }) {
        item { Row { Switch(archived, { archived = it }); Text("Показывать архив") } }
        if (snapshot.apartments.isEmpty()) item { Text("Добавьте квартиру, затем её счётчики и первые показания.") }
        items(snapshot.apartments.filter { archived || !it.archived }, key = { it.id }) { a ->
            HousingCard(a.name + if (a.archived) " · архив" else "", a.address, { onOpenApartment(a.id) }) {
                TratyTextButton(onClick = { editing = a }) { Text("Изменить") }
            }
        }
    }
    editing?.let { ApartmentEditorDialog(it, vm) { editing = null } }
}
