package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton

@Composable
fun MeterDetailScreen(meterId: Long, onBack: () -> Unit, vm: MeterDetailViewModel = viewModel(key = "meter-$meterId")) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val meter = s.meters.firstOrNull { it.id == meterId }
    val registers = s.registers.filter { it.meterId == meterId }
    val ids = registers.map { it.id }.toSet()
    val readings = s.readings.filter { it.registerId in ids }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MeterReading?>(null) }
    var deleting by remember { mutableStateOf<MeterReading?>(null) }
    var submitting by remember { mutableStateOf(false) }
    HousingPage(meter?.name ?: "Счётчик", vm.message, onBack, actions = {
        TratyTextButton(onClick = { adding = true }, enabled = meter != null && !meter.archived) { Text("Показания") }
    }) {
        item { Text("Номер прибора: ${meter?.serialNumber ?: "не указан"}\nПоставщик: ${meter?.provider ?: "не указан"}\nЛицевой счёт: ${meter?.account ?: "не указан"}") }
        item { TratyTextButton(onClick = { submitting = true }, enabled = readings.isNotEmpty()) { Text("Отметить передачу") } }
        item { ConsumptionCard(ConsumptionGroups.lastComparison(s.series(setOf(meterId))), meter?.let { ResourceType.valueOf(it.resource).unit }.orEmpty()) }
        items(readings.sortedWith(compareByDescending<MeterReading> { it.measuredAt }.thenBy { it.registerId }), key = { it.id }) { r ->
            val code = registers.firstOrNull { it.id == r.registerId }?.code.orEmpty()
            val links = s.submissionReadings.filter { it.readingId == r.id }.map { it.submissionId }.toSet()
            val passed = s.submissions.filter { it.id in links }
            HousingCard("${ReadingDates.decode(r.measuredAt)} · ${ReadingValues.format(r.valueMilli)} ${meter?.let { ResourceType.valueOf(it.resource).unit }.orEmpty()} · $code") {
                Text("Внесено: ${java.time.Instant.ofEpochMilli(r.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
                passed.forEach { Text("Отмечено переданным ${ReadingDates.decode(it.submittedAt)}, за ${it.month / 100}-${(it.month % 100).toString().padStart(2, '0')}") }
                if (r.note != null) Text(r.note)
                TratyTextButton(onClick = { editing = r }) { Text("Исправить") }
                TratyTextButton(onClick = { deleting = r }) { Text("Удалить") }
            }
        }
        registers.forEach { reg ->
            val intervals = Consumption.intervals(readings.filter { it.registerId == reg.id }.map { ReadingPoint(it.measuredAt, it.valueMilli) })
            item { Text("Потребление: ${reg.code}") }
            if (intervals.isEmpty()) item { Text("Первое показание — исходная точка. Для расхода нужен следующий замер.") }
            items(intervals.reversed()) { interval -> Text(intervalText(interval, meter?.let { ResourceType.valueOf(it.resource).unit }.orEmpty())) }
        }
    }
    if (adding) ReadingEditorDialog(registers, null, vm) { adding = false }
    editing?.let { r -> ReadingEditorDialog(registers.filter { it.id == r.registerId }, r, vm) { editing = null } }
    if (submitting) SubmissionDialog(meterId, readings, registers, vm) { submitting = false }
    deleting?.let { r -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить показание?") }, text = { Text("Расход соседних интервалов пересчитается. При удалении первого замера начало доступной истории изменится. Связанные отметки передачи будут сняты.") }, confirmButton = { TratyTextButton(onClick = { vm.delete(r.id) { deleting = null } }, enabled = !vm.busy) { Text("Удалить") } }, dismissButton = { TratyTextButton(onClick = { deleting = null }) { Text("Отмена") } }) }
}
