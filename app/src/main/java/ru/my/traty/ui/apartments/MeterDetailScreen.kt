package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.Instant
import java.time.ZoneId

@Composable
fun MeterDetailScreen(meterId: Long, onBack: () -> Unit, vm: MeterDetailViewModel = viewModel(key = "meter-$meterId")) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val meter = s.meters.firstOrNull { it.id == meterId }
    val resource = meter?.let { ResourceType.valueOf(it.resource) }
    val unit = resource?.unit.orEmpty()
    val registers = s.registers.filter { it.meterId == meterId }
    val ids = registers.map { it.id }.toSet()
    val readings = s.readings.filter { it.registerId in ids }
    val series = s.series(setOf(meterId))
    var adding by rememberSaveable(meterId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MeterReading?>(null) }
    var deleting by remember { mutableStateOf<MeterReading?>(null) }
    var submitting by rememberSaveable(meterId) { mutableStateOf(false) }
    var editMeter by rememberSaveable(meterId) { mutableStateOf(false) }
    var section by rememberSaveable(meterId) { mutableStateOf(0) }
    if (editMeter && meter != null) {
        MeterEditorScreen(
            meter, registers.map { it.code },
            viewModel<ApartmentDetailViewModel>(key = "edit-meter-$meterId"),
        ) { editMeter = false }
        return
    }
    HousingPage(
        meter?.name ?: "Счётчик", vm.message, onBack,
        subtitle = listOfNotNull(
            s.apartments.firstOrNull { it.id == meter?.apartmentId }?.name,
            resource?.title,
            "Архив".takeIf { meter?.archived == true },
        ).joinToString(" · "),
        actions = {
            if (meter != null) HousingMenu("Действия со счётчиком", { editMeter = true })
        },
    ) {
        item {
            TratyTonalCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TratyTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
                    Text("Последние показания", style = TratyTheme.typography.titleMedium)
                    series.forEach { reg ->
                        val latest = reg.points.maxByOrNull { it.measuredAt }
                        if (latest != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                                Text(registerTitle(reg.code), style = TratyTheme.typography.labelLarge, color = TratyTheme.colors.primary)
                                HousingValue(ReadingValues.format(latest.valueMilli), unit, large = true)
                                HousingHint("Замер от ${HousingDates.format(latest.measuredAt)}")
                            }
                        }
                    }
                    if (readings.isEmpty()) HousingHint("Первый замер станет началом истории")
                    HousingHint("Накопленное значение на приборе. Расход — разница между замерами.")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                TratyButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { adding = true },
                    enabled = meter != null && !meter.archived,
                ) { Text("Внести показания") }
                TratyOutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submitting = true },
                    enabled = readings.isNotEmpty(),
                ) { Text("Отметить передачу") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                listOf("История", "Расход", "О приборе").forEachIndexed { index, label ->
                    FilterChip(
                        selected = section == index, onClick = { section = index },
                        modifier = Modifier.weight(1f),
                        label = { Text(label, style = TratyTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TratyTheme.colors.primaryContainer,
                            selectedLabelColor = TratyTheme.colors.primary,
                        ),
                    )
                }
            }
        }
        when (section) {
            0 -> {
                item { HousingSectionTitle("История показаний") }
                if (readings.isEmpty()) item { HousingHint("Внесите показания — они появятся здесь с датой снятия и отметкой передачи.") }
                items(
                    readings.sortedWith(compareByDescending<MeterReading> { it.measuredAt }.thenBy { it.registerId }),
                    key = { it.id },
                ) { reading ->
                    val reg = registers.firstOrNull { it.id == reading.registerId }
                    val links = s.submissionReadings.filter { it.readingId == reading.id }.map { it.submissionId }.toSet()
                    val passed = s.submissions.filter { it.id in links }
                    TratyCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(HousingDates.format(reading.measuredAt), style = TratyTheme.typography.titleMedium)
                                    Text(registerTitle(reg?.code.orEmpty()), style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                                }
                                HousingMenu(
                                    "Действия с показанием за ${HousingDates.format(reading.measuredAt)}",
                                    onEdit = { editing = reading },
                                    onDelete = { deleting = reading },
                                )
                            }
                            HousingValue(ReadingValues.format(reading.valueMilli), unit)
                            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                            if (passed.isEmpty()) HousingHint("Передача не отмечена")
                            passed.forEach {
                                Text(
                                    "Отмечено переданным ${HousingDates.format(it.submittedAt)} · за ${HousingDates.formatMonth(it.month)}",
                                    style = TratyTheme.typography.bodySmall,
                                    color = TratyTheme.colors.primary,
                                )
                            }
                            reading.note?.let { HousingHint(it) }
                            Text(
                                "Внесено ${HousingDates.format(Instant.ofEpochMilli(reading.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())}",
                                style = TratyTheme.typography.bodySmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            1 -> {
                item { ConsumptionCard(ConsumptionGroups.lastComparison(series), unit) }
                registers.forEach { reg ->
                    val intervals = Consumption.intervals(
                        readings.filter { it.registerId == reg.id }.map { ReadingPoint(it.measuredAt, it.valueMilli) },
                    )
                    item { HousingSectionTitle("Интервалы · ${registerTitle(reg.code)}") }
                    if (intervals.isEmpty()) item { HousingHint("Для расчёта расхода нужны два замера. Первое показание задаёт исходную точку.") }
                    items(intervals.reversed()) { interval ->
                        TratyCard(Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(TratyTheme.spacing.md)) { ConsumptionIntervalRow(interval, unit) }
                        }
                    }
                }
            }
            2 -> {
                item { HousingSectionTitle("Реквизиты счётчика") }
                item {
                    HousingCard("О приборе") {
                        MeterInfoRow("Ресурс", resource?.title)
                        MeterInfoRow("Номер прибора", meter?.serialNumber)
                        MeterInfoRow("Поставщик", meter?.provider)
                        MeterInfoRow("Лицевой счёт", meter?.account)
                        MeterInfoRow("Начало учёта", meter?.accountingFrom?.let(HousingDates::format))
                        MeterInfoRow("Окончание учёта", meter?.accountingTo?.let(HousingDates::format))
                        MeterInfoRow("Статус", if (meter?.archived == true) "В архиве" else "Действующий")
                    }
                }
            }
        }
    }
    if (adding) ReadingEditorDialog(registers, null, vm, onEditExisting = {
        adding = false; editing = it
    }) { adding = false }
    editing?.let { r -> ReadingEditorDialog(registers.filter { it.id == r.registerId }, r, vm) { editing = null } }
    if (submitting) SubmissionDialog(meterId, readings, registers, vm) { submitting = false }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить показание?") },
            text = { Text("Расход соседних интервалов пересчитается. При удалении первого замера начало доступной истории изменится. Связанные отметки передачи будут сняты.") },
            confirmButton = {
                TratyTextButton(onClick = { vm.delete(r.id) { deleting = null } }, enabled = !vm.busy) { Text("Удалить") }
            },
            dismissButton = { TratyTextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MeterInfoRow(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Text(label, style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "Не указано", style = TratyTheme.typography.bodyMedium)
    }
}
