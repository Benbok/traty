package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.capture.NewCategory
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.ui.designsystem.theme.TratyTheme

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
