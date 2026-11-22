package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    if (categories.isEmpty()) return

    var expanded by remember(categories) { mutableStateOf(false) }
    val allLabel = "Все категории"
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: allLabel
    val selectCategory: (Long?) -> Unit = {
        expanded = false
        onSelect(it)
    }

    Text(
        "Категория расходов",
        style = TratyTheme.typography.labelMedium,
        color = TratyTheme.colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(TratyTheme.spacing.xxs))
    TratyDropdownPicker(
        expanded = expanded,
        onExpand = { expanded = true },
        onDismiss = { expanded = false },
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedName,
                    modifier = Modifier.weight(1f),
                    style = TratyTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        CategoryOption(allLabel, selectedId == null) { selectCategory(null) }
        categories.forEach { category ->
            CategoryOption(category.name, category.id == selectedId) { selectCategory(category.id) }
        }
    }
    Spacer(Modifier.height(TratyTheme.spacing.md))
}

@Composable
private fun CategoryOption(name: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name) },
        onClick = onClick,
        modifier = Modifier.semantics { selected = isSelected },
        trailingIcon = {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TratyTheme.colors.primary)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
    )
}
