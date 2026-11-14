package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Template
import ru.my.traty.data.TemplateWithCategory
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.CategoryPicker

@Composable
fun TemplatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Шаблоны трат",
                subtitle = "Быстрое сохранение частых расходов",
                onBack = onBack,
                actions = {
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Добавить шаблон",
                        onClick = { showAdd = true },
                        containerColor = TratyTheme.colors.primaryContainer,
                        tint = TratyTheme.colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize()
                .padding(horizontal = TratyTheme.spacing.lg),
        ) {
            TratyButton(
                onClick = { showAdd = true },
                modifier = Modifier.padding(vertical = TratyTheme.spacing.sm),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(TratyTheme.spacing.xs))
                Text("Добавить шаблон")
            }

            LazyColumn(Modifier.weight(1f)) {
                items(templates, key = { it.template.id }) { t ->
                    TemplateRow(t, onDelete = { vm.deleteTemplate(t.template) })
                    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                }
            }
        }
    }

    if (showAdd) {
        AddTemplateDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { title, amount, catId, subId, place ->
                vm.addTemplate(title, amount, catId, subId, place)
                showAdd = false
            },
        )
    }
}

@Composable
private fun TemplateRow(t: TemplateWithCategory, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.categoryEmoji} ${t.template.title}",
                style = TratyTheme.typography.titleSmall,
            )
            Text(
                "${t.categoryName} · ${formatRub(t.template.amountKopecks)}" +
                    (t.template.place?.let { " · $it" } ?: ""),
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        TratyIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Удалить",
            onClick = onDelete,
            tint = TratyTheme.colors.expense,
        )
    }
}

@Composable
private fun AddTemplateDialog(
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (title: String, amountKopecks: Long, categoryId: Long, subcategoryId: Long?, place: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.category?.name ?: "") }
    var place by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Новый шаблон", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (напр. Кофе)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                TratyAmountTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(selectedCat, categories, null) { selectedCat = it }
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Место (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    val kopecks = AmountInput.parsePositiveKopecks(amount)
                    if (title.isNotBlank() && kopecks != null) {
                        val cat = categories.firstOrNull { it.category.name == selectedCat }
                        if (cat != null) {
                            onSave(title, kopecks, cat.category.id, null, place.ifBlank { null })
                        }
                    }
                },
            ) { Text("Добавить") }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
