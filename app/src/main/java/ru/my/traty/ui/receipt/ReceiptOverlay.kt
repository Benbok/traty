package ru.my.traty.ui.receipt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.capture.EntryDraft
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Экран чека поверх всего приложения, пока идёт разбор или проверка. [onSave] сохраняет трату как окно проверки фразы. */
@Composable
fun ReceiptOverlay(
    categories: List<CategoryWithSubs>,
    onSave: (EntryDraft) -> Unit,
    vm: ReceiptViewModel = viewModel(),
) {
    val state = vm.state
    if (state == ReceiptUiState.Idle) return
    Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
        ReceiptReviewScreen(
            state = state,
            categories = categories,
            onClose = vm::close,
            onSave = { draft ->
                onSave(draft)
                vm.close()
            },
        )
    }
}
