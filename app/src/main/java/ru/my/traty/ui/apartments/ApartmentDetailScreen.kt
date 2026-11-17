package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton

@Composable
fun ApartmentDetailScreen(apartmentId: Long, onBack: () -> Unit, onOpenMeter: (Long) -> Unit, onOpenResource: (ResourceType) -> Unit, vm: ApartmentDetailViewModel = viewModel(key = "apartment-$apartmentId")) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val apartment = s.apartments.firstOrNull { it.id == apartmentId }
    val meters = s.meters.filter { it.apartmentId == apartmentId }
    var editing by remember { mutableStateOf<Meter?>(null) }
    HousingPage(apartment?.name ?: "Квартира", vm.message, onBack, actions = {
        TratyTextButton(onClick = { editing = Meter(apartmentId = apartmentId, name = "", resource = "COLD_WATER") }, enabled = apartment != null) { Text("Добавить") }
    }) {
        item { Text("Потребление по ресурсам") }
        items(ResourceType.entries.filter { r -> meters.any { it.resource == r.name } }) { r ->
            HousingCard(r.title, "Отдельные счётчики и общий просмотр", { onOpenResource(r) })
        }
        item { Text("Счётчики, включая архивные") }
        if (meters.isEmpty()) item { Text("Добавьте первый счётчик. Оплаты вводятся на странице транзакций.") }
        items(meters, key = { it.id }) { m ->
            HousingCard(m.name + if (m.archived) " · архив" else "", ResourceType.valueOf(m.resource).title, { onOpenMeter(m.id) }) {
                TratyTextButton(onClick = { editing = m }) { Text("Изменить") }
            }
        }
    }
    editing?.let { value -> MeterEditorDialog(value, s.registers.filter { it.meterId == value.id }.map { it.code }, vm) { editing = null } }
}
