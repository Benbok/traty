package ru.my.traty.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.whatsnew.WhatsNewEntry
import ru.my.traty.data.whatsnew.WhatsNewItem
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Окно «Что нового» после обновления. [onOpenTopic] открывает тему справки; «Понятно» и смахивание
 * закрывают окно одинаково: эту версию больше не показываем.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    entries: List<WhatsNewEntry>,
    onDismiss: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TratyTheme.colors.surface,
        shape = TratyTheme.shapes.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = TratyTheme.spacing.xxl, vertical = TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                Text("Что нового", style = TratyTheme.typography.headlineMedium)
                entries.firstOrNull()?.let {
                    Text(
                        "Версия ${it.version}",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                entries.flatMap { it.items }.forEach { item -> WhatsNewItemRow(item, onOpenTopic) }
            }
            TratyButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Понятно") }
        }
    }
}

@Composable
internal fun WhatsNewItemRow(item: WhatsNewItem, onOpenTopic: (String) -> Unit) {
    Column {
        Row {
            Text("•", style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.primary)
            Text(
                item.text,
                style = TratyTheme.typography.bodyLarge,
                color = TratyTheme.colors.onSurface,
                modifier = Modifier.padding(start = TratyTheme.spacing.sm),
            )
        }
        item.topicId?.let { id ->
            TratyTextButton(onClick = { onOpenTopic(id) }) { Text("Как это работает") }
        }
    }
}
