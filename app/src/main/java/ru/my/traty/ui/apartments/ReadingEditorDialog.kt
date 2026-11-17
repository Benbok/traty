package ru.my.traty.ui.apartments

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import ru.my.traty.data.apartments.*
import java.time.LocalDate

@Composable
internal fun ReadingEditorDialog(registers: List<MeterRegister>, existing: MeterReading?, vm: MeterDetailViewModel, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(existing?.let { ReadingDates.decode(it.measuredAt).toString() } ?: LocalDate.now().toString()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val fields = remember { mutableStateMapOf<Long, String>().apply { registers.forEach { put(it.id, if (existing?.registerId == it.id) ReadingValues.format(existing.valueMilli) else "") } } }
    HousingDialog(if (existing == null) "Новые показания" else "Исправить показание", error ?: vm.message, vm.busy, onDismiss, {
        try {
            val date = ReadingDates.encode(LocalDate.parse(day))
            val entered = registers.filter { fields[it.id].orEmpty().isNotBlank() }.map { reg ->
                val value = ReadingValues.parseMilli(fields[reg.id].orEmpty()) ?: throw IllegalArgumentException("Введите неотрицательное число, до трёх знаков после запятой")
                (existing ?: MeterReading(registerId = reg.id, measuredAt = date, valueMilli = value)).copy(measuredAt = date, valueMilli = value, note = note.ifBlank { null })
            }
            require(entered.isNotEmpty()) { "Введите хотя бы одно значение" }
            vm.save(entered, onDismiss)
        } catch (e: Exception) { error = if (e is IllegalArgumentException && e.message?.contains("Введите") == true) e.message else "Проверьте дату: гггг-мм-дд" }
    }) {
        HousingField(day, "Дата снятия: гггг-мм-дд") { day = it }
        registers.forEach { reg -> HousingField(fields[reg.id].orEmpty(), if (reg.code == "TOTAL") "Показание" else "Показание ${reg.code}") { fields[reg.id] = it } }
        HousingField(note, "Заметка — необязательно") { note = it }
        Text("Введите накопленное значение с прибора. Первый замер задаёт исходную точку. Пустой тариф не заполняется автоматически.")
        if (existing != null) Text("Изменение значения или даты снимет связанную отметку передачи. Если дата уже есть в истории, исправьте существующую запись.")
    }
}
