package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.TratyTextButton
import java.time.LocalDate

@Composable
fun ResourceDetailScreen(apartmentId: Long, resource: ResourceType, onBack: () -> Unit, vm: ResourceDetailViewModel = viewModel(key = "resource-$apartmentId-${resource.name}")) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val meters = s.meters.filter { it.apartmentId == apartmentId && it.resource == resource.name }
    val active = meters.filterNot { it.archived }.map { it.id }.toSet()
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    HousingPage(resource.title, vm.message, onBack) {
        item { ConsumptionCard(ConsumptionGroups.lastComparison(s.series(active)), resource.unit) }
        item { HousingField(from, "Начало периода: гггг-мм-дд") { from = it; result = null } }
        item { HousingField(to, "Конец периода: гггг-мм-дд") { to = it; result = null } }
        item { TratyTextButton(onClick = {
            result = try {
                val a = ReadingDates.encode(LocalDate.parse(from)); val b = ReadingDates.encode(LocalDate.parse(to))
                val relevant = meters.filter { (it.accountingFrom == null || it.accountingFrom <= b) && (it.accountingTo == null || it.accountingTo >= a) }.map { it.id }.toSet()
                ConsumptionGroups.forRange(s.series(relevant), a, b)?.let { intervalText(it, resource.unit) }
                    ?: "Точный общий расход за эти даты неизвестен: нужны замеры на обеих границах у всех счётчиков и неизменный состав приборов."
            } catch (_: Exception) { "Проверьте даты периода: гггг-мм-дд" }
        }) { Text("Посмотреть период") } }
        result?.let { text -> item { Text(text) } }
        item { Text("По каждому счётчику — реальные интервалы. Отсутствующий замер не означает нулевой расход.") }
        items(meters, key = { it.id }) { m ->
            HousingCard(m.name + if (m.archived) " · архив" else "") {
                val series = s.series(setOf(m.id))
                series.forEach { reg ->
                    Text(reg.code)
                    Consumption.intervals(reg.points).takeLast(6).forEach { Text(intervalText(it, resource.unit)) }
                }
            }
        }
    }
}
