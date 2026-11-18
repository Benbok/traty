package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Поля прокручиваются независимо от закреплённых внизу действий. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingEditorSheet(
    title: String,
    busy: Boolean,
    canSave: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    voice: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val busyNow by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busyNow },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Text(
                title,
                style = TratyTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg)
                    .padding(bottom = TratyTheme.spacing.md),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = TratyTheme.spacing.lg)
                    .padding(bottom = TratyTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
                content = content,
            )
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            ReadingEditorActions(busy, canSave, error, onDismiss, onSave, voice)
        }
    }
}

@Composable
private fun ReadingEditorActions(
    busy: Boolean,
    canSave: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    voice: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        if (error != null) Text(
            error, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.expense,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        voice()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            TratyOutlinedButton(
                onClick = onDismiss, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = TratyTheme.spacing.huge),
            ) { Text("Отмена") }
            TratyButton(
                onClick = onSave, enabled = !busy && canSave,
                modifier = Modifier.weight(1f).heightIn(min = TratyTheme.spacing.huge),
            ) { Text(if (busy) "Сохранение…" else "Сохранить") }
        }
    }
}
