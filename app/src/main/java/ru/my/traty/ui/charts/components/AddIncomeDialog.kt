package ru.my.traty.ui.charts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalDate

private val INCOME_SOURCES = listOf(
    "Зарплата" to "💼",
    "Аванс" to "💰",
    "Перевод" to "💳",
    "Возврат" to "↩️",
    "Подработка" to "🛠️",
    "Прочее" to "📦",
)

/**
 * Диалог добавления дохода / пополнения (дебет).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIncomeDialog(
    onDismiss: () -> Unit,
    onSave: (amountRub: Double, source: String, note: String?, date: LocalDate) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("Зарплата") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                text = "Добавить доход / дебет",
                style = TratyTheme.typography.headlineMedium,
                color = TratyTheme.colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("Сумма дохода, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = TratyTheme.colors.expense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                Text(
                    text = "Источник поступления",
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    INCOME_SOURCES.forEach { (name, emoji) ->
                        val isSelected = selectedSource == name
                        Surface(
                            shape = TratyTheme.shapes.small,
                            color = if (isSelected) TratyTheme.colors.incomeContainer else TratyTheme.colors.surfaceContainer,
                            border = if (isSelected) BorderStroke(1.dp, TratyTheme.colors.income) else null,
                            modifier = Modifier.clickable { selectedSource = name },
                        ) {
                            Text(
                                text = "$emoji $name",
                                style = TratyTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) TratyTheme.colors.income else TratyTheme.colors.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Дата (ГГГГ-ММ-ДД)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    val amount = amountText.replace(',', '.').replace(" ", "").toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errorText = "Введите корректную сумму"
                        return@TratyButton
                    }
                    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: LocalDate.now()
                    onSave(amount, selectedSource, note.trim().ifBlank { null }, date)
                },
            ) {
                Text("Сохранить доход")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
