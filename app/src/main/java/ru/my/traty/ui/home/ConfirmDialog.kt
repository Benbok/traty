package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.ui.Draft
import ru.my.traty.ui.DraftItem
import ru.my.traty.ui.NewCategory
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Диалог проверки и подтверждения распознанных трат.
 */
@Composable
fun ConfirmDialog(
    draft: Draft,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
) {
    var items by remember(draft) { mutableStateOf(draft.items) }
    fun update(i: Int, f: (DraftItem) -> DraftItem) {
        items = items.toMutableList().also { it[i] = f(it[i]) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                if (items.size > 1) "Проверьте траты (${items.size})" else "Проверьте трату",
                style = TratyTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (draft.rawText.isNotBlank()) {
                    Text(
                        "«${draft.rawText}»",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                }
                items.forEachIndexed { i, item ->
                    TratyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TratyTheme.spacing.xs),
                        backgroundColor = TratyTheme.colors.surfaceContainer,
                    ) {
                        Column(
                            Modifier.padding(TratyTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { v -> update(i) { d -> d.copy(amountText = v) } },
                                    label = { Text("Сумма, ₽") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = TratyTheme.shapes.small,
                                )
                                if (items.size > 1) {
                                    IconButton(onClick = { items = items.filterIndexed { j, _ -> j != i } }) {
                                        Icon(Icons.Filled.Close, "Убрать", tint = TratyTheme.colors.onSurfaceVariant)
                                    }
                                }
                            }
                            CategoryPicker(item.category, categories, item.suggestedCategory) { name ->
                                update(i) { d -> d.copy(category = name) }
                            }
                            OutlinedTextField(
                                value = item.subcategory,
                                onValueChange = { v -> update(i) { d -> d.copy(subcategory = v) } },
                                label = { Text("Подкатегория") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.place,
                                onValueChange = { v -> update(i) { d -> d.copy(place = v) } },
                                label = { Text("Место") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.note,
                                onValueChange = { v -> update(i) { d -> d.copy(note = v) } },
                                label = { Text("Заметка") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            OutlinedTextField(
                                value = item.dateText,
                                onValueChange = { v -> update(i) { d -> d.copy(dateText = v) } },
                                label = { Text("Дата (ГГГГ-ММ-ДД)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TratyButton(onClick = { onSave(draft.copy(items = items)) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
fun CategoryPicker(
    selected: String,
    categoryList: List<CategoryWithSubs>,
    suggested: NewCategory?,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val categories = categoryList.map { it.category }
    // Предложение модели показываем, только если такой категории ещё нет в базе
    val newOne = suggested?.takeIf { s -> categories.none { it.name.lowercase() == s.name.lowercase() } }
    val isNew = newOne != null && selected == newOne.name
    val emoji = categories.firstOrNull { it.name == selected }?.emoji ?: newOne?.takeIf { isNew }?.emoji ?: ""
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        ) {
            Text("$emoji $selected" + if (isNew) " (новая)" else "")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            newOne?.let { s ->
                DropdownMenuItem(
                    text = { Text("${s.emoji} ${s.name} (новая)") },
                    onClick = { onPick(s.name); open = false },
                )
                HorizontalDivider()
            }
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.emoji} ${c.name}") },
                    onClick = { onPick(c.name); open = false },
                )
            }
        }
    }
}
