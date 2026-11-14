package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.data.isUserEditable
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Данные для редактирования одной траты.
 */
data class EditFields(
    val amountText: String,
    val category: String,
    val subcategory: String,
    val place: String,
    val note: String,
    val dateText: String,
)

/**
 * Диалог редактирования существующей траты.
 * Кнопки: «Сохранить», «Удалить», «Отмена».
 * Для записей сверки баланса поля заблокированы.
 */
@Composable
fun EditExpenseDialog(
    item: ExpenseWithCategory,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (EditFields) -> Unit,
    onDelete: () -> Unit,
    labels: List<ExpenseLabelHit> = emptyList(),
) {
    val e = item.expense
    val editable = e.isUserEditable
    val zone = remember { ZoneId.systemDefault() }
    val date = remember(e.timestamp) {
        Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
    }
    val initialAmount = remember(e.amountKopecks) {
        if (e.amountKopecks % 100 == 0L) (e.amountKopecks / 100).toString()
        else (e.amountKopecks / 100.0).toString()
    }

    var fields by remember(item) {
        mutableStateOf(
            EditFields(
                amountText = initialAmount,
                category = item.categoryName,
                subcategory = item.subcategoryName.orEmpty(),
                place = e.place.orEmpty(),
                note = e.note.orEmpty(),
                dateText = date.toString(),
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                if (editable) "Редактировать трату" else "Запись сверки",
                style = TratyTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (labels.isNotEmpty()) {
                    ExpenseLabelExplanations(labels)
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                }
                if (!editable) {
                    Text(
                        "Записи сверки баланса нельзя редактировать. Используйте «Переделать последнюю сверку» в настройках.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                }

                OutlinedTextField(
                    value = fields.amountText,
                    onValueChange = { fields = fields.copy(amountText = it) },
                    label = { Text("Сумма, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                CategoryPicker(
                    selected = fields.category,
                    categoryList = categories,
                    suggested = null,
                    onPick = { fields = fields.copy(category = it) },
                )

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                OutlinedTextField(
                    value = fields.subcategory,
                    onValueChange = { fields = fields.copy(subcategory = it) },
                    label = { Text("Подкатегория") },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                OutlinedTextField(
                    value = fields.place,
                    onValueChange = { fields = fields.copy(place = it) },
                    label = { Text("Место") },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                OutlinedTextField(
                    value = fields.note,
                    onValueChange = { fields = fields.copy(note = it) },
                    label = { Text("Заметка") },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                DateField(
                    dateText = fields.dateText,
                    onDateTextChange = { fields = fields.copy(dateText = it) },
                    enabled = editable,
                )

                // Исходная фраза
                e.rawText?.let { raw ->
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                    Text(
                        "Исходный текст: «$raw»",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                if (editable) {
                    TratyTextButton(
                        onClick = onDelete,
                        contentColor = TratyTheme.colors.expense,
                    ) {
                        Text("Удалить")
                    }
                }
                TratyButton(
                    onClick = { onSave(fields) },
                    enabled = editable,
                ) {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
