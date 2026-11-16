package ru.my.traty.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import ru.my.traty.capture.EntryRow
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.items.ItemsBalance
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.home.DateField
import ru.my.traty.ui.home.ExpenseItemsCard

/** Поля проверки чека: что исправить, сумма, магазин, категория, дата, товары и распознанный текст. */
@Composable
internal fun ReceiptForm(
    row: EntryRow,
    notices: List<String>,
    receiptText: String,
    balance: ItemsBalance?,
    categories: List<CategoryWithSubs>,
    onChange: (EntryRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sp = TratyTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.md),
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        if (notices.isNotEmpty()) item(key = "notices") { NoticesCard(notices) }
        item(key = "fields") { MainFields(row, categories, onChange) }
        item(key = "items") {
            Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
                ExpenseItemsCard(
                    items = row.items,
                    totalKopecks = row.kopecks(),
                    enabled = true,
                    onChange = { onChange(row.copy(items = it)) },
                )
                if (balance?.needsAttention == true) MismatchHint(balance)
            }
        }
        item(key = "privacy") {
            Text(
                "Фото ушло в Yandex Vision OCR, текст чека без кассира и номеров кассы — в YandexGPT. На телефоне фото не хранится.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        item(key = "text") { ReceiptTextCard(receiptText) }
    }
}

@Composable
private fun MainFields(row: EntryRow, categories: List<CategoryWithSubs>, onChange: (EntryRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        TratyAmountTextField(
            value = row.amountText,
            onValueChange = { onChange(row.copy(amountText = it)) },
            label = "Итог чека",
            isError = row.kopecks() == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = row.place,
            onValueChange = { onChange(row.copy(place = it)) },
            label = { Text("Магазин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        )
        CategoryPicker(row.category, categories, null) { onChange(row.copy(category = it)) }
        DateField(dateText = row.dateText, onDateTextChange = { onChange(row.copy(dateText = it)) })
    }
}

@Composable
private fun NoticesCard(notices: List<String>) {
    TratyCard(Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surfaceContainer) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
            Text("Проверьте", style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
            notices.forEach { Text("• $it", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant) }
        }
    }
}

/** Цены товаров не сходятся с итогом: подсказка, где искать ошибку. */
@Composable
private fun MismatchHint(b: ItemsBalance) {
    val diff = formatRub(kotlin.math.abs(b.differenceKopecks))
    Text(
        if (b.differenceKopecks < 0) "Товары дороже итога на $diff: проверьте цены, где-то могла не учесться скидка"
        else "Товары дешевле итога на $diff: возможно, какая-то строка чека не распозналась — добавьте её",
        style = TratyTheme.typography.bodySmall,
        color = TratyTheme.colors.expense,
        modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
    )
}

/** Распознанный текст чека по кнопке: видно, что прочитал Яндекс, если товары разобрались странно. */
@Composable
private fun ReceiptTextCard(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        TratyTextButton(onClick = { open = !open }) {
            Text(if (open) "Скрыть распознанный текст" else "Показать распознанный текст")
        }
        if (open) {
            TratyCard(Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surfaceContainer) {
                Text(
                    text,
                    style = TratyTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TratyTheme.colors.onSurface,
                    modifier = Modifier.padding(TratyTheme.spacing.md),
                )
            }
        }
    }
}
