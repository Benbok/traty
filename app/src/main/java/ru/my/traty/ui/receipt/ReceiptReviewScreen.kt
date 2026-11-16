package ru.my.traty.ui.receipt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.ReceiptReview
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.items.ItemsCheck
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Экран чека поверх ленты: ожидание разбора, ошибка или обязательная проверка перед сохранением.
 * Трата с фото никогда не сохраняется сама — только по кнопке «Сохранить» на этом экране.
 */
@Composable
fun ReceiptReviewScreen(
    state: ReceiptUiState,
    categories: List<CategoryWithSubs>,
    onClose: () -> Unit,
    onSave: (EntryDraft) -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (state is ReceiptUiState.Review) "Проверьте чек" else "Чек по фото",
                subtitle = "Яндекс читает чек, сумму сверяет приложение",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                onBack = onClose,
            )
        },
    ) { pad ->
        val content = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()
        when (state) {
            is ReceiptUiState.Review -> ReceiptReviewContent(state.review, categories, onSave, content)
            is ReceiptUiState.Working -> ReceiptWaiting(state.step.title, content)
            is ReceiptUiState.Failed -> ReceiptFailed(state.message, onClose, content)
            ReceiptUiState.Idle -> Unit
        }
    }
}

@Composable
private fun ReceiptReviewContent(
    review: ReceiptReview,
    categories: List<CategoryWithSubs>,
    onSave: (EntryDraft) -> Unit,
    modifier: Modifier,
) {
    var row by remember(review) { mutableStateOf(review.draft.rows.single()) }
    val total = row.kopecks()
    val balance = total?.let { ItemsCheck.balance(it, row.items.map { item -> item.priceKopecks() }) }
    Column(modifier.imePadding()) {
        ReceiptForm(
            row = row,
            notices = review.notices,
            receiptText = review.receiptText,
            balance = balance,
            categories = categories,
            onChange = { row = it },
            modifier = Modifier.weight(1f),
        )
        TratyButton(
            onClick = { onSave(review.draft.copy(rows = listOf(row))) },
            enabled = total != null,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        ) {
            Text(if (balance?.needsAttention == true) "Сохранить с расхождением" else "Сохранить")
        }
    }
}

@Composable
private fun ReceiptWaiting(step: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            CircularProgressIndicator(color = TratyTheme.colors.primary)
            Text(step, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurface)
            Text(
                "Обычно это занимает 5–15 секунд",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReceiptFailed(message: String, onClose: () -> Unit, modifier: Modifier) {
    Box(modifier.padding(TratyTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text("Чек не разобран", style = TratyTheme.typography.titleMedium, color = TratyTheme.colors.onSurface)
            Text(
                message,
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TratyOutlinedButton(onClick = onClose) { Text("Закрыть") }
        }
    }
}
