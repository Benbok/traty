package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import ru.my.traty.data.apartments.*
import ru.my.traty.ui.stats.ModeChip
import java.time.LocalDate

@Composable
internal fun MeterEditorDialog(value: Meter, existingCodes: List<String>, vm: ApartmentDetailViewModel, onDismiss: () -> Unit) {
    var name by remember(value.id) { mutableStateOf(value.name) }
    var resource by remember(value.id) { mutableStateOf(ResourceType.valueOf(value.resource)) }
    var serial by remember(value.id) { mutableStateOf(value.serialNumber.orEmpty()) }
    var provider by remember(value.id) { mutableStateOf(value.provider.orEmpty()) }
    var account by remember(value.id) { mutableStateOf(value.account.orEmpty()) }
    var count by remember(value.id) { mutableStateOf(existingCodes.size.coerceAtLeast(1)) }
    var from by remember(value.id) { mutableStateOf(value.accountingFrom?.let { ReadingDates.decode(it).toString() }.orEmpty()) }
    var to by remember(value.id) { mutableStateOf(value.accountingTo?.let { ReadingDates.decode(it).toString() }.orEmpty()) }
    var archived by remember(value.id) { mutableStateOf(value.archived) }
    var error by remember { mutableStateOf<String?>(null) }
    HousingDialog("Счётчик", error ?: vm.message, vm.busy, onDismiss, {
        try {
            val codes = if (resource != ResourceType.ELECTRICITY || count == 1) listOf("TOTAL") else (1..count).map { "T$it" }
            vm.saveMeter(value.copy(name = name, resource = resource.name, serialNumber = serial.ifBlank { null }, provider = provider.ifBlank { null }, account = account.ifBlank { null }, archived = archived,
                accountingFrom = from.takeIf { it.isNotBlank() }?.let { ReadingDates.encode(LocalDate.parse(it)) },
                accountingTo = to.takeIf { it.isNotBlank() }?.let { ReadingDates.encode(LocalDate.parse(it)) }), codes, onDismiss)
        } catch (_: Exception) { error = "Введите даты в формате гггг-мм-дд" }
    }) {
        HousingField(name, "Название, например «Кухня»") { name = it }
        if (value.id == 0L) ResourceType.entries.forEach { r -> ModeChip(r.title, resource == r) { resource = r } }
        if (value.id == 0L && resource == ResourceType.ELECTRICITY) Row { (1..3).forEach { n -> ModeChip("$n тариф", count == n) { count = n } } }
        HousingField(serial, "Номер прибора — необязательно") { serial = it }
        HousingField(provider, "Поставщик — необязательно") { provider = it }
        HousingField(account, "Лицевой счёт — необязательно") { account = it }
        HousingField(from, "Начало истории: гггг-мм-дд, или первый замер") { from = it }
        HousingField(to, "Конец истории: гггг-мм-дд, или пусто") { to = it }
        if (value.id != 0L) Row { Checkbox(archived, { archived = it; if (it && to.isBlank()) to = LocalDate.now().toString() }); Text("Прибор в архиве") }
    }
}
