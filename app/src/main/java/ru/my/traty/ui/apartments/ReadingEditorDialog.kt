package ru.my.traty.ui.apartments

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.capture.meter.MeterVoiceParser
import ru.my.traty.data.apartments.*
import java.time.LocalDate

@Composable
internal fun ReadingEditorDialog(
    registers: List<MeterRegister>,
    existing: MeterReading?,
    vm: MeterDetailViewModel,
    onEditExisting: ((MeterReading) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(existing?.let { HousingDates.format(it.measuredAt) } ?: HousingDates.format(LocalDate.now())) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var lastVoice by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceBusy by remember { mutableStateOf(false) }
    var fields by rememberSaveable { mutableStateOf(registers.associate { it.id to
        (if (existing?.registerId == it.id) ReadingValues.format(existing.valueMilli) else "") }) }
    var confirmedHigh by remember(fields, day) { mutableStateOf(false) }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val history = snapshot.readings.filter { it.id != existing?.id }
    val date = runCatching { ReadingDates.encode(HousingDates.parse(day)) }.getOrNull()
    val checks = registers.mapNotNull { reg ->
        val value = ReadingValues.parseMilli(fields[reg.id].orEmpty())
        if (value == null || date == null) null else reg to
            checkReading(date, value, history.filter { it.registerId == reg.id }.map { ReadingPoint(it.measuredAt, it.valueMilli) })
    }
    val high = checks.any { it.second.unusuallyHigh }
    val blocked = checks.any { it.second.error != null }
    HousingDialog(
        if (existing == null) "Новые показания" else "Исправить показание",
        error ?: vm.message, vm.busy, onDismiss,
        onSave = {
            try {
                require(!voiceBusy && !blocked && (!high || confirmedHigh)) { "Проверьте показания перед сохранением" }
                vm.save(enteredReadings(registers, fields, day, note, existing), onDismiss)
            } catch (e: IllegalArgumentException) { error = e.message }
        },
        saveEnabled = !voiceBusy && !blocked && (!high || confirmedHigh) && fields.values.any { it.isNotBlank() },
    ) {
        ReadingVoiceInput(!vm.busy, onText = { text ->
            lastVoice = text
            try {
                val draft = MeterVoiceParser.parse(text, registers.map { it.code })
                val updated = registers.mapNotNull { reg -> draft.valuesMilli[reg.code]?.let { reg.id to ReadingValues.format(it) } }.toMap()
                fields = fields + updated
                draft.date?.let { day = HousingDates.format(it) }
                error = null
            } catch (e: IllegalArgumentException) { error = e.message }
        }, onError = { error = it }, onBusy = { voiceBusy = it })
        lastVoice?.let { Text("Распознано: «" + it + "»") }
        HousingField(day, "Дата снятия: дд.мм.гггг") { day = it; error = null }
        registers.forEach { reg ->
            HousingField(fields[reg.id].orEmpty(), if (reg.code == "TOTAL") "Показание" else "Показание " + reg.code) {
                fields = fields + (reg.id to it); error = null
            }
        }
        ReadingCheckPreview(checks, history, date, snapshot, onEditExisting)
        if (high) HighReadingConfirmation(confirmedHigh) { confirmedHigh = it }
        HousingField(note, "Заметка — необязательно") { note = it }
        Text("Проверьте числа перед сохранением. Если дата не названа, остаётся дата из формы. Пустые тарифы не заполняются автоматически.")
        if (existing != null) Text("Изменение значения или даты снимет связанную отметку передачи.")
    }
}

private fun enteredReadings(
    registers: List<MeterRegister>, fields: Map<Long, String>, day: String, note: String, existing: MeterReading?,
): List<MeterReading> {
    val parsedDate = try { HousingDates.parse(day) }
        catch (_: Exception) { throw IllegalArgumentException("Проверьте дату: дд.мм.гггг") }
    require(!parsedDate.isAfter(LocalDate.now())) { "Дата снятия показаний не может быть в будущем" }
    val date = ReadingDates.encode(parsedDate)
    return registers.filter { fields[it.id].orEmpty().isNotBlank() }.map { reg ->
        val value = ReadingValues.parseMilli(fields[reg.id].orEmpty())
            ?: throw IllegalArgumentException("Введите неотрицательное число, до трёх знаков после запятой")
        (existing ?: MeterReading(registerId = reg.id, measuredAt = date, valueMilli = value))
            .copy(measuredAt = date, valueMilli = value, note = note.ifBlank { null })
    }.also { require(it.isNotEmpty()) { "Введите хотя бы одно значение" } }
}
