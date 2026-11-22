package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.ChartExpenseCategory
import ru.my.traty.ui.designsystem.components.TratyDropdownPicker
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun CashFlowCategoryPicker(
    categories: List<ChartExpenseCategory>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember(categories) { mutableStateOf(false) }
    val allLabel = "Все категории"
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: allLabel

    Text("Категория расходов", style = TratyTheme.typography.labelMedium)
    TratyDropdownPicker(
        expanded = expanded,
        onExpand = { expanded = true },
        onDismiss = { expanded = false },
        label = {
            Text(selectedName, style = TratyTheme.typography.labelMedium)
            Spacer(Modifier.width(TratyTheme.spacing.xs))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    ) {
        DropdownMenuItem(
            text = { Text(allLabel) },
            onClick = {
                expanded = false
                onSelect(null)
            },
        )
        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    expanded = false
                    onSelect(category.id)
                },
            )
        }
    }
    Spacer(Modifier.height(TratyTheme.spacing.md))
}
