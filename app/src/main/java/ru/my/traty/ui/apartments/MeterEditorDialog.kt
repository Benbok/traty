package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalDate

@Composable
internal fun MeterEditorScreen(value: Meter, existingCodes: List<String>, vm: ApartmentDetailViewModel, onDismiss: () -> Unit) {
    val isNew = value.id == 0L
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var resourceName by rememberSaveable(value.id) { mutableStateOf(value.resource) }
    val resource = ResourceType.valueOf(resourceName)
    var serial by rememberSaveable(value.id) { mutableStateOf(value.serialNumber.orEmpty()) }
    var provider by rememberSaveable(value.id) { mutableStateOf(value.provider.orEmpty()) }
    var account by rememberSaveable(value.id) { mutableStateOf(value.account.orEmpty()) }
    var count by rememberSaveable(value.id) { mutableStateOf(existingCodes.size.coerceAtLeast(1)) }
    var from by rememberSaveable(value.id) { mutableStateOf(value.accountingFrom?.let { HousingDates.format(it) }.orEmpty()) }
    var to by rememberSaveable(value.id) { mutableStateOf(value.accountingTo?.let { HousingDates.format(it) }.orEmpty()) }
    var archived by rememberSaveable(value.id) { mutableStateOf(value.archived) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    HousingEditorPage(
        title = if (isNew) "Новый счётчик" else value.name,
        subtitle = if (isNew) "Создание счётчика" else "Редактирование счётчика",
        isNew = isNew,
        busy = vm.busy,
        canSave = name.isNotBlank(),
        error = error ?: vm.message,
        onDismiss = onDismiss,
        onSave = {
            try {
                val codes = if (resource != ResourceType.ELECTRICITY || count == 1) listOf("TOTAL") else (1..count).map { "T$it" }
                vm.saveMeter(
                    value.copy(
                        name = name.trim(), resource = resource.name,
                        serialNumber = serial.trim().ifBlank { null },
                        provider = provider.trim().ifBlank { null },
                        account = account.trim().ifBlank { null }, archived = archived,
                        accountingFrom = from.takeIf { it.isNotBlank() }?.let { ReadingDates.encode(HousingDates.parse(it)) },
                        accountingTo = to.takeIf { it.isNotBlank() }?.let { ReadingDates.encode(HousingDates.parse(it)) },
                    ),
                    codes, onDismiss,
                )
                error = null
            } catch (_: java.time.format.DateTimeParseException) {
                error = "Введите даты в формате дд.мм.гггг"
            }
        },
    ) {
        HousingEditorPreview(
            emoji = when (resource) {
                ResourceType.COLD_WATER -> "💧"
                ResourceType.HOT_WATER -> "♨️"
                ResourceType.ELECTRICITY -> "⚡"
                ResourceType.GAS -> "🔥"
            },
            title = name.ifBlank { "Новый счётчик" },
            subtitle = resource.title + if (resource == ResourceType.ELECTRICITY) " · Тарифов: $count" else "",
        )
        TratyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                TratyInlineRow("Название", name, placeholder = "Например, Кухня", enabled = !vm.busy) { name = it }
                TratyRowDivider()
                TratyInlineRow("Номер прибора", serial, placeholder = "Необязательно", enabled = !vm.busy) { serial = it }
                TratyRowDivider()
                TratyInlineRow("Поставщик", provider, placeholder = "Необязательно", enabled = !vm.busy) { provider = it }
                TratyRowDivider()
                TratyInlineRow("Лицевой счёт", account, placeholder = "Необязательно", enabled = !vm.busy) { account = it }
            }
        }
        if (isNew) {
            HousingSectionTitle("Ресурс")
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(TratyTheme.spacing.md)) {
                    ResourceType.entries.forEach { r ->
                        FilterChip(
                            selected = resource == r,
                            onClick = { resourceName = r.name },
                            enabled = !vm.busy,
                            label = { Text(r.title) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TratyTheme.colors.primaryContainer,
                                selectedLabelColor = TratyTheme.colors.primary,
                            ),
                        )
                    }
                }
            }
            if (resource == ResourceType.ELECTRICITY) {
                HousingSectionTitle("Количество тарифов")
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    (1..3).forEach { n ->
                        FilterChip(
                            selected = count == n,
                            onClick = { count = n },
                            enabled = !vm.busy,
                            label = { Text(n.toString()) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TratyTheme.colors.primaryContainer,
                                selectedLabelColor = TratyTheme.colors.primary,
                            ),
                        )
                    }
                }
            }
        }
        HousingSectionTitle("Период учёта")
        TratyCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                TratyInlineRow("Начало", from, placeholder = "дд.мм.гггг", enabled = !vm.busy) { from = it; error = null }
                TratyRowDivider()
                TratyInlineRow("Окончание", to, placeholder = "дд.мм.гггг", enabled = !vm.busy) { to = it; error = null }
                if (!isNew) {
                    TratyRowDivider()
                    TratySwitchRow(
                        title = "Прибор в архиве",
                        subtitle = "История показаний сохранится",
                        checked = archived,
                        enabled = !vm.busy,
                    ) {
                        archived = it
                        if (it && to.isBlank()) to = HousingDates.format(LocalDate.now())
                    }
                }
            }
        }
        Text(
            "Начало можно оставить пустым — история начнётся с первого замера. Окончание укажите, когда прибор перестал использоваться.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}
