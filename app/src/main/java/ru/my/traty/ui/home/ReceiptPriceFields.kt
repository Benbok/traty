package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.capture.DraftItem
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ReceiptPriceFields(item: DraftItem, onChange: (DraftItem) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        TratyAmountTextField(
            value = item.unitPriceText,
            onValueChange = { onChange(item.copy(unitPriceText = it)) },
            label = "Цена за единицу",
            modifier = Modifier.weight(1f),
        )
        TratyAmountTextField(
            value = item.discountText,
            onValueChange = { onChange(item.copy(discountText = it)) },
            label = "Скидка",
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "Сверьте цену за позицию с чеком с учётом скидки. В покупку записывается цена за позицию.",
        style = TratyTheme.typography.bodySmall,
        color = TratyTheme.colors.onSurfaceVariant,
    )
}
