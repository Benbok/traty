package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton

@Composable
fun ApartmentsRoute(onBack: () -> Unit) {
    var apartment by rememberSaveable { mutableStateOf<Long?>(null) }
    var meter by rememberSaveable { mutableStateOf<Long?>(null) }
    var resource by rememberSaveable { mutableStateOf<String?>(null) }
    val apartmentId = apartment
    val meterId = meter
    val resourceName = resource
    when {
        meterId != null -> MeterDetailScreen(meterId, { meter = null })
        apartmentId != null && resourceName != null -> ResourceDetailScreen(apartmentId, ResourceType.valueOf(resourceName), { resource = null }, onOpenMeter = { meter = it })
        apartmentId != null -> ApartmentDetailScreen(apartmentId, { apartment = null }, { meter = it }, { resource = it.name })
        else -> ApartmentsScreen(onOpenApartment = { apartment = it }, onBack = onBack)
    }
}

@Composable
fun ApartmentsScreen(onOpenApartment: (Long) -> Unit, onBack: () -> Unit, vm: ApartmentsViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var archived by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Apartment?>(null) }
    val active = snapshot.apartments.filterNot { it.archived }
    val archivedApartments = snapshot.apartments.filter { it.archived }
    val visible = if (archived) archivedApartments else active
    editing?.let {
        ApartmentEditorScreen(it, vm) { editing = null }
        return
    }
    HousingPage(
        "Квартиры",
        vm.message,
        subtitle = "Счётчики, показания и потребление",
        onBack = onBack,
        onAdd = { editing = Apartment(name = "") },
        addDescription = "Добавить квартиру",
        actions = {
            TratyIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Добавить квартиру",
                onClick = { editing = Apartment(name = "") },
                tint = TratyTheme.colors.primary,
                containerColor = TratyTheme.colors.primaryContainer,
            )
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                FilterChip(
                    selected = !archived,
                    onClick = { archived = false },
                    label = { Text("Активные (${active.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
                FilterChip(
                    selected = archived,
                    onClick = { archived = true },
                    label = { Text("Архив (${archivedApartments.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
            }
        }
        if (visible.isEmpty()) item {
            TratyEmptyState(
                icon = Icons.Filled.Apartment,
                title = if (archived) "Архив пуст" else "Добавьте первую квартиру",
                description = if (archived) "Здесь появятся квартиры, перенесённые в архив"
                    else "Храните показания счётчиков и следите за расходом воды, света и газа",
                action = if (archived) null else {
                    { TratyTextButton(onClick = { editing = Apartment(name = "") }) { Text("Добавить квартиру") } }
                },
            )
        }
        items(visible, key = { it.id }) { a ->
            val meters = snapshot.meters.filter { it.apartmentId == a.id && !it.archived }
            ApartmentSummaryCard(
                value = a,
                meterCount = meters.size,
                resources = ResourceType.entries.filter { r -> meters.any { it.resource == r.name } },
                onOpen = { onOpenApartment(a.id) },
                onEdit = { editing = a },
            )
        }
    }
}
