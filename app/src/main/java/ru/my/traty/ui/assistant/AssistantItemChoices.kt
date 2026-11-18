package ru.my.traty.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ru.my.traty.assistant.ItemSuggestion
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun AssistantItemChoices(
    messageId: Long,
    choices: List<ItemSuggestion>,
    onCalculate: (Set<Int>) -> Unit,
) {
    if (choices.size == 1) {
        TratyTextButton(onClick = { onCalculate(setOf(0)) }) {
            Text(choices.single().itemName ?: "Показать расчёт", style = TratyTheme.typography.labelMedium)
        }
        return
    }

    var selected by rememberSaveable(messageId) { mutableStateOf(arrayListOf<Int>()) }
    Column {
        choices.forEachIndexed { index, choice ->
            val checked = index in selected
            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(checked, role = Role.Checkbox) { enabled ->
                        selected = ArrayList(if (enabled) selected + index else selected - index)
                    }
                    .padding(vertical = TratyTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                val name = choice.itemName ?: "Вариант ${index + 1}"
                Text(name, style = TratyTheme.typography.bodyMedium, modifier = Modifier.padding(start = TratyTheme.spacing.sm))
            }
        }
        TratyTextButton(onClick = { onCalculate(selected.toSet()) }, enabled = selected.isNotEmpty()) {
            Text("Посчитать выбранное", style = TratyTheme.typography.labelMedium)
        }
    }
}
