package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun HousingPage(
    title: String,
    message: String?,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    onAdd: (() -> Unit)? = null,
    addDescription: String = "Добавить",
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    // Экраны уже находятся внутри TratyRoot, который обрабатывает системные отступы.
    Scaffold(
        containerColor = TratyTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TratyHeaderCard(
                title = title,
                subtitle = subtitle,
                icon = Icons.Filled.Apartment,
                onBack = onBack,
                actions = actions,
            )
        },
        floatingActionButton = {
            if (onAdd != null) FloatingActionButton(
                onClick = onAdd,
                containerColor = TratyTheme.colors.primary,
                contentColor = TratyTheme.colors.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = addDescription)
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad),
            contentPadding = PaddingValues(
                start = TratyTheme.spacing.lg,
                end = TratyTheme.spacing.lg,
                top = TratyTheme.spacing.sm,
                bottom = if (onAdd != null) 88.dp else TratyTheme.spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            if (message != null) item {
                TratyTonalCard {
                    Text(
                        message,
                        modifier = Modifier.padding(TratyTheme.spacing.md),
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.expense,
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun HousingSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = TratyTheme.spacing.sm),
        style = TratyTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = TratyTheme.colors.onSurfaceVariant,
    )
}

@Composable
internal fun HousingCard(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    TratyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            Modifier.padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    Text(
                        title,
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TratyTheme.colors.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) Text(
                        subtitle,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                if (onClick != null) Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TratyTheme.colors.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
internal fun HousingField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.medium,
        singleLine = true,
    )
}

@Composable
internal fun HousingDialog(title: String, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        containerColor = TratyTheme.colors.surface,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            content()
            if (error != null) Text(error, color = TratyTheme.colors.expense)
        } },
        confirmButton = { TratyTextButton(onClick = onSave, enabled = !busy) { Text("Сохранить") } },
        dismissButton = { TratyTextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}
