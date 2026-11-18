package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.DatePickerModal
import java.time.LocalDate

@Composable
fun ResourceDetailScreen(
    apartmentId: Long,
    resource: ResourceType,
    onBack: () -> Unit,
    onOpenMeter: (Long) -> Unit = {},
    vm: ResourceDetailViewModel = viewModel(key = "resource-$apartmentId-${resource.name}"),
) {
    BackHandler(onBack = onBack)
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val meters = snapshot.meters.filter { it.apartmentId == apartmentId && it.resource == resource.name }
    val active = meters.filterNot { it.archived }.map { it.id }.toSet()
    val series = remember(snapshot, active) { snapshot.series(active) }
    val meterIds = meters.map { it.id }.toSet()
    val allSeries = remember(snapshot, meterIds) { snapshot.series(meterIds) }
    HousingPage(
        resource.title,
        vm.message,
        onBack,
        subtitle = "Потребление · ${snapshot.apartments.firstOrNull { it.id == apartmentId }?.name.orEmpty()}",
    ) {
        item(key = "resource-period") { ResourcePeriodCard(allSeries, series, resource.unit) }
        item { ConsumptionCard(ConsumptionGroups.lastComparison(series), resource.unit) }
        item { HousingSectionTitle("По счётчикам") }
        item { HousingHint("У каждого прибора свои интервалы. Пропущенный замер не считается нулевым расходом.") }
        items(meters, key = { it.id }) { meter ->
            HousingCard(
                title = meter.name,
                subtitle = if (meter.archived) "Архивный прибор" else resource.title,
                onClick = { onOpenMeter(meter.id) },
            ) {
                snapshot.series(setOf(meter.id)).forEach { reg ->
                    val intervals = Consumption.intervals(reg.points).takeLast(3).reversed()
                    HousingSectionTitle(registerTitle(reg.code))
                    if (intervals.isEmpty()) HousingHint("Для расчёта нужны два показания")
                    intervals.forEach { interval -> ConsumptionIntervalRow(interval, resource.unit) }
                }
            }
        }
    }
}

@Composable
private fun ResourcePeriodCard(allSeries: List<RegisterSeries>, series: List<RegisterSeries>, unit: String) {
    val today = LocalDate.now()
    var fromText by rememberSaveable { mutableStateOf(HousingDates.format(today.minusMonths(6))) }
    var toText by rememberSaveable { mutableStateOf(HousingDates.format(today)) }
    var choosingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showTotal by rememberSaveable(fromText, toText) { mutableStateOf(false) }
    val from = remember(fromText) { runCatching { ReadingDates.encode(HousingDates.parse(fromText)) }.getOrNull() }
    val to = remember(toText) { runCatching { ReadingDates.encode(HousingDates.parse(toText)) }.getOrNull() }
    val valid = from != null && to != null && from < to
    val trend = remember(series, from, to) {
        if (from != null && to != null && from < to) ConsumptionTrend.forResource(series, from, to) else emptyList()
    }
    val total = remember(allSeries, from, to, showTotal) {
        if (!showTotal || from == null || to == null) null
        else ConsumptionPeriod.calculate(allSeries, from, to)
    }
    TratyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            Text("Динамика потребления", style = TratyTheme.typography.titleMedium)
            HousingHint("Средний расход в сутки по действующим счётчикам")
            PeriodDateField(fromText, "С даты", { choosingStart = true }) { fromText = it }
            PeriodDateField(toText, "По дату", { choosingStart = false }) { toText = it }
            when {
                from == null || to == null -> HousingHint("Укажите обе даты в формате дд.мм.гггг.")
                !valid -> HousingHint("Начало периода должно быть раньше окончания.")
                trend.isEmpty() -> HousingHint("Нет интервалов с общими датами замеров по действующим счётчикам за этот период.")
                else -> ConsumptionTrendPlot(trend, from, to, unit)
            }
            TratyOutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = valid,
                onClick = { showTotal = true },
            ) { Text("Рассчитать расход за период") }
            if (showTotal && valid) {
                if (total == null) HousingHint("Нет данных для расчёта. Нужны два замера одного счётчика, чтобы получился интервал, пересекающийся с выбранным периодом.")
                else PeriodTotal(total, unit)
            }
        }
    }
    choosingStart?.let { start ->
        DatePickerModal(
            initialDate = (if (start) from else to)?.let(ReadingDates::decode),
            latestDate = today,
            onDateSelected = {
                if (start) fromText = HousingDates.format(it) else toText = HousingDates.format(it)
            },
            onDismiss = { choosingStart = null },
        )
    }
}

@Composable
private fun PeriodTotal(result: ConsumptionPeriodResult, unit: String) {
    val title = when {
        !result.complete && result.estimated -> "Оценка по доступным замерам"
        !result.complete -> "Расход по доступным замерам"
        result.estimated -> "Оценка расхода за период"
        else -> "Расход за выбранный период"
    }
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Text(title, style = TratyTheme.typography.titleSmall)
        HousingValue(ReadingValues.format(result.milli), unit)
        if (!result.complete) HousingHint("Замеры покрывают только часть периода или есть не по всем счётчикам.")
        if (result.estimated) HousingHint("Расход на границах периода рассчитан пропорционально числу дней между замерами.")
    }
}

@Composable
private fun PeriodDateField(value: String, label: String, onPick: () -> Unit, onChange: (String) -> Unit) {
    HousingField(value, label, trailingIcon = {
        TratyIconButton(Icons.Filled.DateRange, "Открыть календарь. $label", onPick)
    }, onChange = onChange)
}
