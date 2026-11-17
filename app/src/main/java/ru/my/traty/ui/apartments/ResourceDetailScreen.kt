package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
fun ResourceDetailScreen(
    apartmentId: Long,
    resource: ResourceType,
    onBack: () -> Unit,
    onOpenMeter: (Long) -> Unit = {},
    vm: ResourceDetailViewModel = viewModel(key = "resource-$apartmentId-${resource.name}"),
) {
    BackHandler(onBack = onBack)
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val meters = s.meters.filter { it.apartmentId == apartmentId && it.resource == resource.name }
    val active = meters.filterNot { it.archived }.map { it.id }.toSet()
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<ConsumptionInterval?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    HousingPage(resource.title, vm.message, onBack, subtitle = "Потребление · ${s.apartments.firstOrNull { it.id == apartmentId }?.name.orEmpty()}") {
        item { ConsumptionCard(ConsumptionGroups.lastComparison(s.series(active)), resource.unit) }
        item { HousingSectionTitle("Расход за выбранный период") }
        item {
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
                    HousingField(from, "С даты · дд.мм.гггг") { from = it; result = null; error = null }
                    HousingField(to, "По дату · дд.мм.гггг") { to = it; result = null; error = null }
                    TratyOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = from.isNotBlank() && to.isNotBlank(),
                        onClick = {
                            error = null
                            result = try {
                                val a = ReadingDates.encode(HousingDates.parse(from))
                                val b = ReadingDates.encode(HousingDates.parse(to))
                                require(a < b)
                                val relevant = meters.filter {
                                    (it.accountingFrom == null || it.accountingFrom <= b) &&
                                        (it.accountingTo == null || it.accountingTo >= a)
                                }.map { it.id }.toSet()
                                ConsumptionGroups.forRange(s.series(relevant), a, b).also {
                                    if (it == null) error = "Нужны замеры на обеих границах у всех счётчиков и неизменный состав приборов."
                                }
                            } catch (_: Exception) {
                                error = "Проверьте даты: дд.мм.гггг. Начало периода должно быть раньше окончания."
                                null
                            }
                        },
                    ) { Text("Рассчитать расход") }
                    result?.let { ConsumptionIntervalRow(it, resource.unit, "За выбранные даты") }
                    error?.let { HousingHint(it) }
                }
            }
        }
        item { HousingSectionTitle("По счётчикам") }
        item { HousingHint("У каждого прибора свои интервалы. Пропущенный замер не считается нулевым расходом.") }
        items(meters, key = { it.id }) { meter ->
            HousingCard(
                title = meter.name,
                subtitle = if (meter.archived) "Архивный прибор" else resource.title,
                onClick = { onOpenMeter(meter.id) },
            ) {
                s.series(setOf(meter.id)).forEach { reg ->
                    val intervals = Consumption.intervals(reg.points).takeLast(3).reversed()
                    HousingSectionTitle(registerTitle(reg.code))
                    if (intervals.isEmpty()) HousingHint("Для расчёта нужны два показания")
                    intervals.forEach { interval -> ConsumptionIntervalRow(interval, resource.unit) }
                }
            }
        }
    }
}
