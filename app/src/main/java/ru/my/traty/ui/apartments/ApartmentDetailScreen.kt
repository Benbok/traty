package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
fun ApartmentDetailScreen(apartmentId: Long, onBack: () -> Unit, onOpenMeter: (Long) -> Unit, onOpenResource: (ResourceType) -> Unit, vm: ApartmentDetailViewModel = viewModel(key = "apartment-$apartmentId")) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val apartment = s.apartments.firstOrNull { it.id == apartmentId }
    val meters = s.meters.filter { it.apartmentId == apartmentId }
    var editing by remember { mutableStateOf<Meter?>(null) }
    var editApartment by rememberSaveable(apartmentId) { mutableStateOf(false) }
    var consumption by rememberSaveable(apartmentId) { mutableStateOf(false) }
    editing?.let { value ->
        MeterEditorScreen(value, s.registers.filter { it.meterId == value.id }.map { it.code }, vm) { editing = null }
        return
    }
    if (editApartment && apartment != null) {
        ApartmentEditorScreen(apartment, viewModel<ApartmentsViewModel>(key = "edit-apartment-$apartmentId")) { editApartment = false }
        return
    }
    val addMeter = { editing = Meter(apartmentId = apartmentId, name = "", resource = "COLD_WATER") }
    HousingPage(
        apartment?.name ?: "Квартира",
        vm.message,
        onBack,
        subtitle = apartment?.address?.takeIf { it.isNotBlank() } ?: "Счётчики и расход ресурсов",
        onAdd = if (apartment == null) null else addMeter,
        addDescription = "Добавить счётчик",
        actions = {
            TratyIconButton(
                Icons.Filled.Add, "Добавить счётчик", addMeter,
                enabled = apartment != null,
                tint = TratyTheme.colors.primary,
                containerColor = TratyTheme.colors.primaryContainer,
            )
            if (apartment != null) HousingMenu("Действия с квартирой", { editApartment = true })
        },
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                FilterChip(
                    selected = !consumption, onClick = { consumption = false },
                    label = { Text("Счётчики") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
                FilterChip(
                    selected = consumption, onClick = { consumption = true },
                    label = { Text("Потребление") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
            }
        }
        if (meters.isEmpty()) item {
            TratyEmptyState(
                icon = Icons.Filled.Speed, title = "Пока нет счётчиков",
                description = "Добавьте прибор и первое показание, чтобы начать отслеживать потребление",
                action = { TratyTextButton(onClick = addMeter, enabled = apartment != null) { Text("Добавить счётчик") } },
            )
        }
        if (consumption) {
            if (meters.isNotEmpty()) item { HousingHint("Сводка по активным приборам. Откройте ресурс, чтобы выбрать период и увидеть вклад каждого счётчика.") }
            items(ResourceType.entries.filter { r -> meters.any { it.resource == r.name } }, key = { it.name }) { resource ->
                ResourceSummaryCard(resource, meters.filter { it.resource == resource.name }, s) { onOpenResource(resource) }
            }
        } else {
            ResourceType.entries.forEach { resource ->
                val group = meters.filter { it.resource == resource.name && !it.archived }
                if (group.isNotEmpty()) {
                    item { HousingSectionTitle(resource.title) }
                    items(group, key = { it.id }) { meter ->
                        MeterSummaryCard(meter, s, onOpen = { onOpenMeter(meter.id) }, onEdit = { editing = meter })
                    }
                }
            }
            val archived = meters.filter { it.archived }
            if (archived.isNotEmpty()) {
                item { HousingSectionTitle("Архивные приборы") }
                items(archived, key = { it.id }) { meter ->
                    MeterSummaryCard(meter, s, onOpen = { onOpenMeter(meter.id) }, onEdit = { editing = meter })
                }
            }
        }
    }
}
