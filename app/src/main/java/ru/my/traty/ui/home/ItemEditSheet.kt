package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ru.my.traty.capture.DraftItem
import ru.my.traty.data.items.ItemNames
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Правка одного товара в шторке: название, цена за позицию, количество и единица. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemEditSheet(
    initial: DraftItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDone: (DraftItem) -> Unit,
    onDelete: () -> Unit,
    receiptDetails: Boolean = false,
) {
    var item by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text(if (isNew) "Новый товар" else "Товар", style = TratyTheme.typography.titleMedium)
            OutlinedTextField(
                value = item.name,
                onValueChange = { item = item.copy(name = it) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            )
            TratyAmountTextField(
                value = item.priceText,
                onValueChange = { item = item.copy(priceText = it) },
                label = "Цена за позицию",
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Сколько заплачено за этот товар целиком. Можно оставить пустой") },
                isError = receiptDetails && item.priceText.isNotBlank() && item.priceKopecks() == null,
            )
            QuantityRow(item) { item = it }
            if (receiptDetails) ReceiptPriceFields(item) { item = it }
            OutlinedTextField(
                value = item.packText,
                onValueChange = { item = item.copy(packText = it.take(20)) },
                label = { Text("Фасовка одной штуки") },
                placeholder = { Text("200 мл, 500 г, 1 л") },
                singleLine = true,
                isError = item.packText.isNotBlank() && item.pack() == null,
                supportingText = { Text("Объём или вес упаковки: по нему ИИ посчитает литры и килограммы") },
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            )
            val canSave = ItemNames.normalize(item.name) != null &&
                (!receiptDetails || item.priceText.isBlank() || item.priceKopecks() != null)
            SheetButtons(isNew, canSave, onDelete) { onDone(item.copy(name = item.name.trim())) }
        }
    }
}

@Composable
private fun QuantityRow(item: DraftItem, onChange: (DraftItem) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        OutlinedTextField(
            value = item.qtyText,
            onValueChange = { onChange(item.copy(qtyText = it)) },
            label = { Text("Количество") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            shape = TratyTheme.shapes.small,
        )
        OutlinedTextField(
            value = item.unit,
            onValueChange = { onChange(item.copy(unit = it.take(10))) },
            label = { Text("шт, кг, л") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = TratyTheme.shapes.small,
        )
    }
}

@Composable
private fun SheetButtons(isNew: Boolean, canSave: Boolean, onDelete: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        if (!isNew) {
            TratyTextButton(onClick = onDelete) { Text("Удалить товар", color = TratyTheme.colors.expense) }
        }
        TratyButton(onClick = onDone, enabled = canSave, modifier = Modifier.weight(1f)) {
            Text(if (isNew) "Добавить" else "Готово")
        }
    }
}
