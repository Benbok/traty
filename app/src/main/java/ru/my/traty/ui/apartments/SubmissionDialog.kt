package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import ru.my.traty.data.apartments.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun SubmissionDialog(meterId: Long, readings: List<MeterReading>, vm: MeterDetailViewModel, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<Long>().apply { addAll(readings.groupBy { it.registerId }.values.mapNotNull { rs -> rs.maxByOrNull { it.measuredAt }?.id }) } }
    HousingDialog("Отметить передачу", error ?: vm.message, vm.busy, onDismiss, {
        try {
            val period = YearMonth.parse(month)
            vm.submitted(meterId, selected.toList(), ReadingDates.encode(LocalDate.parse(day)), period.year * 100 + period.monthValue, onDismiss)
        } catch (_: Exception) { error = "Проверьте дату и месяц передачи" }
    }) {
        Text("Это ваша отметка. Приложение не отправляет данные поставщику и не подтверждает их приём.")
        HousingField(day, "Когда переданы: гггг-мм-дд") { day = it }
        HousingField(month, "За какой месяц: гггг-мм") { month = it }
        readings.sortedByDescending { it.measuredAt }.forEach { r ->
            Row {
                Checkbox(r.id in selected, { checked -> if (checked) selected.add(r.id) else selected.remove(r.id) })
                Text("${ReadingDates.decode(r.measuredAt)} · ${ReadingValues.format(r.valueMilli)}")
            }
        }
    }
}
