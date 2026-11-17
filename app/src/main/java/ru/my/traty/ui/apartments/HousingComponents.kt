package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.*
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun HousingPage(title: String, message: String?, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: LazyListScope.() -> Unit) {
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = { TratyHeaderCard(title = title, modifier = Modifier.statusBarsPadding(), onBack = onBack, actions = actions) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad), contentPadding = PaddingValues(TratyTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            if (message != null) item { Text(message, color = TratyTheme.colors.expense) }
            content()
        }
    }
}

@Composable
internal fun HousingCard(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    TratyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(TratyTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
            Text(title, style = TratyTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = TratyTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
internal fun HousingField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, singleLine = true)
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
