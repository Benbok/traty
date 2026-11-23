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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ru.my.traty.capture.EntryRow
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.items.ItemsCheck
import ru.my.traty.data.items.ItemNames
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
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
    onEdit: (EntryRow) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    onParse: () -> Unit = {},
    onRemovePhoto: (Int) -> Unit,
    onMovePhoto: (Int, Int) -> Unit,
    onReturnToPhotos: () -> Unit,
    onCancelRecognition: () -> Unit,
    onFillManually: () -> Unit,
    photoButton: @Composable ((String) -> Unit) -> Unit,
    retakeButton: @Composable (Long, (String) -> Unit) -> Unit,
) {
    val onBack = when (state) {
        is ReceiptUiState.Working -> onCancelRecognition
        is ReceiptUiState.Review -> onReturnToPhotos
        else -> onClose
    }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (state is ReceiptUiState.Review) "Проверьте чек" else "Чек по фото",
                subtitle = when (state) {
                    is ReceiptUiState.Review -> "Проверьте позиции и итог перед сохранением"
                    else -> "Съёмка и распознавание"
                },
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                onBack = onBack,
            )
        },
    ) { pad ->
        val content = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()
        when (state) {
            is ReceiptUiState.Photos -> ReceiptPhotosContent(
                state.photos, onRemovePhoto, onMovePhoto, onParse, photoButton, retakeButton, content,
            )
            is ReceiptUiState.Review -> ReceiptReviewContent(state, categories, onEdit, onSave, content)
            is ReceiptUiState.Working -> ReceiptWaiting(state, onCancelRecognition, content)
            is ReceiptUiState.Failed -> ReceiptFailed(state, onRetry, onReturnToPhotos, onFillManually, content)
            ReceiptUiState.Idle -> Unit
        }
    }
}

@Composable
private fun ReceiptReviewContent(
    state: ReceiptUiState.Review,
    categories: List<CategoryWithSubs>,
    onEdit: (EntryRow) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    val review = state.review
    val row = review.draft.rows.single()
    val total = row.kopecks()
    val balance = total?.let { ItemsCheck.balance(it, row.items.map { item -> item.priceKopecks() }) }
    Column(modifier.imePadding()) {
        ReceiptForm(
            row = row,
            notices = review.notices,
            balance = balance,
            categories = categories,
            onChange = onEdit,
            modifier = Modifier.weight(1f),
        )
        state.saveError?.let {
            Text(it, color = TratyTheme.colors.expense, modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg))
        }
        TratyButton(
            onClick = onSave,
            enabled = !state.saving && total != null && row.dateText.isNotBlank() && row.items.all { ItemNames.normalize(it.name) != null },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        ) {
            Text(when {
                state.saving -> "Сохраняем…"
                balance != null && balance.differenceKopecks != 0L && row.items.isNotEmpty() -> "Сохранить с расхождением"
                else -> "Сохранить"
            })
        }
    }
}

@Composable
private fun ReceiptWaiting(state: ReceiptUiState.Working, onCancel: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            CircularProgressIndicator(color = TratyTheme.colors.primary)
            Text(state.title, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurface)
            if (state.canCancel) TratyTextButton(onClick = onCancel) { Text("Отменить") }
        }
    }
}

@Composable
private fun ReceiptFailed(failed: ReceiptUiState.Failed, onRetry: () -> Unit, onPhotos: () -> Unit, onFillManually: () -> Unit, modifier: Modifier) {
    Box(modifier.padding(TratyTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text("Чек не разобран", style = TratyTheme.typography.titleMedium, color = TratyTheme.colors.onSurface)
            Text(
                failed.message,
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (failed.canRetry) {
                // Фото ещё в памяти: снимать заново не нужно
                TratyButton(onClick = onRetry) { Text("Повторить") }
            }
            TratyOutlinedButton(onClick = onPhotos) { Text("Вернуться к снимкам") }
            TratyTextButton(onClick = onFillManually) { Text("Заполнить вручную") }
        }
    }
}

