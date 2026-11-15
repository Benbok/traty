package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.tooling.preview.Preview
import ru.my.traty.ai.EntryType
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.EntryOrigin
import ru.my.traty.capture.EntryRow
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.IncomeSources
import ru.my.traty.data.sameName
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.home.DateField

/**
 * Общий экран проверки фразы: траты и пополнения одним списком, у каждой строки переключатель типа.
 * Показывается как диалог — так же выглядят остальные подтверждения и он работает внутри прозрачной панели записи.
 */
@Composable
fun EntryConfirmSheet(
    draft: EntryDraft,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (EntryDraft) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    var rows by remember(draft) { mutableStateOf(draft.rows) }
    fun update(i: Int, f: (EntryRow) -> EntryRow) {
        rows = rows.toMutableList().also { it[i] = f(it[i]) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                if (rows.size > 1) "Проверьте записи (${rows.size})" else "Проверьте запись",
                style = TratyTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (notice != null) {
                    Text(notice, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.expense)
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                }
                if (draft.rawText.isNotBlank()) {
                    Text(
                        "«${draft.rawText}»",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                }
                rows.forEachIndexed { i, row ->
                    val isIncome = row.type == EntryType.INCOME
                    TratyCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.xs),
                        backgroundColor = if (isIncome) TratyTheme.colors.incomeContainer else TratyTheme.colors.surfaceContainer,
                    ) {
                        Column(
                            Modifier.padding(TratyTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = !isIncome,
                                    onClick = { update(i) { it.copy(type = EntryType.EXPENSE) } },
                                    label = { Text("Трата") },
                                )
                                Spacer(Modifier.padding(horizontal = TratyTheme.spacing.xxs))
                                FilterChip(
                                    selected = isIncome,
                                    onClick = { update(i) { it.copy(type = EntryType.INCOME) } },
                                    label = { Text("Пополнение") },
                                )
                                Spacer(Modifier.weight(1f))
                                if (rows.size > 1) {
                                    IconButton(onClick = { rows = rows.filterIndexed { j, _ -> j != i } }) {
                                        Icon(Icons.Filled.Close, "Убрать", tint = TratyTheme.colors.onSurfaceVariant)
                                    }
                                }
                            }
                            TratyAmountTextField(
                                value = row.amountText,
                                onValueChange = { v -> update(i) { it.copy(amountText = v) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (isIncome) {
                                IncomeSourcePicker(row.source) { name -> update(i) { it.copy(source = name) } }
                            } else {
                                CategoryPicker(row.category, categories, row.suggestedCategory) { name ->
                                    update(i) { it.copy(category = name) }
                                }
                                OutlinedTextField(
                                    value = row.subcategory,
                                    onValueChange = { v -> update(i) { it.copy(subcategory = v) } },
                                    label = { Text("Подкатегория") },
                                    supportingText = if (isNewSubcategory(row, categories)) {
                                        { Text("Новая, создастся при сохранении") }
                                    } else null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = TratyTheme.shapes.small,
                                )
                                OutlinedTextField(
                                    value = row.place,
                                    onValueChange = { v -> update(i) { it.copy(place = v) } },
                                    label = { Text("Место") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = TratyTheme.shapes.small,
                                )
                            }
                            OutlinedTextField(
                                value = row.note,
                                onValueChange = { v -> update(i) { it.copy(note = v) } },
                                label = { Text("Заметка") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = TratyTheme.shapes.small,
                            )
                            DateField(
                                dateText = row.dateText,
                                onDateTextChange = { v -> update(i) { it.copy(dateText = v) } },
                            )
                        }
                    }
                }
                TratyTextButton(onClick = { rows = rows + EntryRow.empty() }) { Text("+ строка") }
            }
        },
        confirmButton = {
            TratyButton(onClick = { onSave(draft.copy(rows = rows)) }) {
                Text(if (rows.size > 1) "Сохранить ${rows.size}" else "Сохранить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Подкатегории нет в выбранной категории (или сама категория новая) — её создаст сохранение. */
private fun isNewSubcategory(row: EntryRow, categories: List<CategoryWithSubs>): Boolean {
    val sub = row.subcategory.trim()
    if (sub.isEmpty()) return false
    val cat = categories.firstOrNull { sameName(it.category.name, row.category) } ?: return true
    return cat.subs.none { sameName(it.name, sub) }
}

@Composable
private fun IncomeSourcePicker(selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val emoji = IncomeSources.ALL.firstOrNull { it.first == selected }?.second.orEmpty()
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        ) {
            Text("$emoji $selected".trim())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            IncomeSources.ALL.forEach { (name, e) ->
                DropdownMenuItem(
                    text = { Text("$e $name") },
                    onClick = { onPick(name); open = false },
                )
            }
        }
    }
}

@Preview(name = "Confirm: mixed", showBackground = true)
@Composable
private fun EntryConfirmSheetPreview() {
    val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Кафе и рестораны", emoji = "☕"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Транспорт", emoji = "🚕"), emptyList()),
    )
    val base = EntryRow.empty()
    val draft = EntryDraft(
        rows = listOf(
            base.copy(amountText = "250", category = "Кафе и рестораны", note = "кофе"),
            base.copy(amountText = "400", category = "Транспорт", note = "такси"),
            base.copy(type = EntryType.INCOME, amountText = "80000", source = "Зарплата"),
        ),
        rawText = "кофе 250, такси 400 и пришла зарплата 80 тысяч",
        origin = EntryOrigin.VOICE,
    )
    TratyTheme(darkTheme = false) {
        EntryConfirmSheet(draft, cats, onDismiss = {}, onSave = {})
    }
}
