package ru.my.traty.ui.apartments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Форма по образцу EditCategoryScreen; системные отступы уже обработаны TratyRoot. */
@Composable
internal fun HousingEditorPage(
    title: String,
    subtitle: String,
    isNew: Boolean,
    busy: Boolean,
    canSave: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val close = { if (!busy) onDismiss() }
    BackHandler(onBack = close)
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = TratyTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TratyHeaderCard(title = title, subtitle = subtitle, onBack = close)
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            ) {
                TratyButton(
                    onClick = onSave,
                    enabled = canSave && !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (busy) "Сохранение…" else if (isNew) "Добавить" else "Сохранить")
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            content()
            if (error != null) Text(
                error,
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.expense,
            )
        }
    }
}

@Composable
internal fun HousingEditorPreview(emoji: String, title: String, subtitle: String) {
    TratyCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(TratyTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            TratyCategoryBadge(emoji = emoji, categoryName = title, size = 56.dp)
            Text(
                title,
                style = TratyTheme.typography.headlineMedium,
                color = TratyTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
