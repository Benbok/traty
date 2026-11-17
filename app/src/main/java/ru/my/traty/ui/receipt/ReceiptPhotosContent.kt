package ru.my.traty.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.capture.ReceiptParts
import ru.my.traty.ai.receipt.ReceiptText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Успешные снимки не храним: показываем начало прочитанного текста, чтобы найти неудачную часть. */
@Composable
internal fun ReceiptPhotosContent(
    texts: List<String>,
    onRemove: (Int) -> Unit,
    onParse: () -> Unit,
    photoButton: @Composable ((String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sp = TratyTheme.spacing
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(sp.lg),
            verticalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            item {
                Text("Один чек — одна покупка", style = TratyTheme.typography.titleMedium)
                Text(
                    "Снимайте сверху вниз с пересечением в 2–3 полные строки товаров. " +
                        "Захватите итог на последнем снимке. Повторы на стыках будут исключены, затем проверьте товары и сумму.",
                    style = TratyTheme.typography.bodyMedium,
                )
                Text("Добавлено снимков: ${texts.size} из ${ReceiptParts.MAX_PHOTOS}")
            }
            itemsIndexed(texts) { index, text ->
                TratyCard(Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surfaceContainer) {
                    Column(Modifier.padding(sp.md)) {
                        Text("Снимок ${index + 1}", style = TratyTheme.typography.titleSmall)
                        Text(ReceiptText.clean(text).lineSequence().take(4).joinToString("\n"), style = TratyTheme.typography.bodySmall)
                        TratyTextButton(onClick = { onRemove(index) }) { Text("Удалить снимок") }
                    }
                }
            }
            item { photoButton { error = it } }
            error?.let { message -> item { Text(message, color = TratyTheme.colors.expense) } }
        }
        TratyButton(
            onClick = onParse,
            enabled = texts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = sp.lg, vertical = sp.sm),
        ) { Text("Разобрать чек") }
    }
}
