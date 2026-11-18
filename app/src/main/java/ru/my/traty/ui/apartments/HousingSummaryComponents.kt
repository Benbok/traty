package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.util.Locale

internal val ResourceType.symbol: ImageVector get() = when (this) {
    ResourceType.COLD_WATER -> Icons.Filled.WaterDrop
    ResourceType.HOT_WATER -> Icons.Filled.HotTub
    ResourceType.ELECTRICITY -> Icons.Filled.Bolt
    ResourceType.GAS -> Icons.Filled.LocalFireDepartment
}

internal fun registerTitle(code: String) = if (code == "TOTAL") "Общее показание" else "Тариф $code"

@Composable
internal fun HousingBadge(icon: ImageVector) {
    Surface(shape = TratyTheme.shapes.medium, color = TratyTheme.colors.primaryContainer) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TratyTheme.colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun HousingMenu(
    description: String,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TratyIconButton(Icons.Filled.MoreVert, description, { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Редактировать") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { expanded = false; onEdit() },
            )
            if (onDelete != null) DropdownMenuItem(
                text = { Text("Удалить", color = TratyTheme.colors.expense) },
                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = TratyTheme.colors.expense) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
internal fun HousingHint(text: String) {
    Text(text, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurfaceVariant)
}

@Composable
internal fun HousingValue(value: String, unit: String, large: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = (if (large) TratyTheme.typography.heroAmount else TratyTheme.typography.largeAmount)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = TratyTheme.colors.onSurface,
        )
        if (unit.isNotBlank()) Text(
            unit, style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ApartmentSummaryCard(
    value: Apartment,
    meterCount: Int,
    resources: List<ResourceType>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    TratyCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HousingBadge(Icons.Filled.Apartment)
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(value.name, style = TratyTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    value.address?.takeIf { it.isNotBlank() }?.let { HousingHint(it) }
                }
                HousingMenu("Действия с квартирой ${value.name}", onEdit)
            }
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                Text(
                    if (value.archived) "Квартира в архиве" else "Активных счётчиков: $meterCount",
                    modifier = Modifier.weight(1f),
                    style = TratyTheme.typography.labelLarge,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                resources.forEach { Icon(it.symbol, it.title, tint = TratyTheme.colors.primary, modifier = Modifier.size(18.dp)) }
                Icon(Icons.Filled.ChevronRight, null, tint = TratyTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MeterSummaryCard(value: Meter, snapshot: ApartmentSnapshot, onOpen: () -> Unit, onEdit: (() -> Unit)? = null) {
    val resource = ResourceType.valueOf(value.resource)
    val series = snapshot.series(setOf(value.id))
    TratyCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HousingBadge(resource.symbol)
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(value.name, style = TratyTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (value.archived) "Архив · ${resource.title}" else resource.title,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                if (onEdit != null) HousingMenu("Действия со счётчиком ${value.name}", onEdit)
                else Icon(Icons.Filled.ChevronRight, null, tint = TratyTheme.colors.onSurfaceVariant)
            }
            val latest = series.mapNotNull { reg -> reg.points.maxByOrNull { it.measuredAt }?.let { reg to it } }
            if (latest.isEmpty()) HousingHint("Нет показаний · откройте счётчик для ввода")
            else {
                HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                latest.forEach { (reg, point) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(registerTitle(reg.code), style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                            Text(HousingDates.format(point.measuredAt), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                        }
                        HousingValue(ReadingValues.format(point.valueMilli), resource.unit, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResourceSummaryCard(resource: ResourceType, meters: List<Meter>, snapshot: ApartmentSnapshot, onOpen: () -> Unit) {
    val active = meters.filterNot { it.archived }
    val comparison = ConsumptionGroups.lastComparison(snapshot.series(active.map { it.id }.toSet()))
    TratyTonalCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(resource.symbol, null, tint = TratyTheme.colors.primary)
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text(resource.title, modifier = Modifier.weight(1f), style = TratyTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ChevronRight, null, tint = TratyTheme.colors.onSurfaceVariant)
            }
            if (comparison == null) HousingHint("Общий расход пока не рассчитан")
            else {
                HousingValue(ReadingValues.format(comparison.current.milli), resource.unit, large = true)
                HousingHint("${HousingDates.format(comparison.current.from)} — ${HousingDates.format(comparison.current.to)}")
            }
            Text(
                "Активных приборов: ${active.size} · Подробнее о потреблении",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConsumptionIntervalRow(interval: ConsumptionInterval, unit: String, label: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        if (label != null) Text(label, style = TratyTheme.typography.labelLarge, color = TratyTheme.colors.primary)
        Text(
            "${HousingDates.format(interval.from)} — ${HousingDates.format(interval.to)}",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        HousingValue(ReadingValues.format(interval.milli), unit)
        Text(
            "${interval.days} дн. · ${String.format(Locale.forLanguageTag("ru"), "%.3f", interval.daily)} $unit в сутки",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}
