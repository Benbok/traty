package ru.my.traty.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.weekly.WeeklyTaskType
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.stats.ModeChip

private val CUSTOM_TYPES = listOf(
    WeeklyTaskType.CATEGORY_LIMIT to "Лимит",
    WeeklyTaskType.COUNT_LIMIT to "Число трат",
    WeeklyTaskType.NO_SPEND_DAYS to "Дни без трат",
)

/** Своё задание: вид, категория и цель (сумма, число трат или дней). */
@Composable
fun AddWeeklyTaskDialog(
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (WeeklyTaskType, CategoryWithSubs, Long) -> Unit,
) {
    var type by remember { mutableStateOf(WeeklyTaskType.CATEGORY_LIMIT) }
    var categoryName by remember { mutableStateOf(categories.firstOrNull()?.category?.name.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var count by remember { mutableLongStateOf(2L) }
    val category = categories.firstOrNull { it.category.name == categoryName }
    val target = if (type == WeeklyTaskType.CATEGORY_LIMIT) AmountInput.parsePositiveKopecks(amount) else count

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Своё задание на неделю", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    CUSTOM_TYPES.forEach { (t, title) ->
                        ModeChip(title = title, selected = type == t, onClick = {
                            type = t
                            count = if (t == WeeklyTaskType.NO_SPEND_DAYS) 3L else 2L
                        })
                    }
                }
                CategoryPicker(categoryName, categories, null) { categoryName = it }
                when (type) {
                    WeeklyTaskType.CATEGORY_LIMIT ->
                        TratyAmountTextField(value = amount, onValueChange = { amount = it }, label = "Не больше за неделю", modifier = Modifier.fillMaxWidth())
                    WeeklyTaskType.NO_SPEND_DAYS -> Stepper("Дней без трат", count, 1L..6L) { count = it }
                    else -> Stepper("Не больше трат", count, 1L..20L) { count = it }
                }
            }
        },
        confirmButton = {
            TratyButton(
                enabled = category != null && target != null,
                onClick = { if (category != null && target != null) onSave(type, category, target) },
            ) { Text("Добавить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Stepper(label: String, value: Long, range: LongRange, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TratyTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TratyIconButton(Icons.Filled.Remove, "Меньше", onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first)
        Text("$value", style = TratyTheme.typography.titleMedium)
        TratyIconButton(Icons.Filled.Add, "Больше", onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last)
    }
}
