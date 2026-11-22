package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
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
import ru.my.traty.ui.charts.model.CashFlowCategory
import ru.my.traty.ui.charts.model.CashFlowCategoryKey
import ru.my.traty.ui.charts.model.CashFlowKind
import ru.my.traty.ui.charts.model.MAX_COMPARED_CATEGORIES
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.TratyDropdownPicker
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun CashFlowCategoryPicker(
    kind: CashFlowKind,
    categories: List<CashFlowCategory>,
    selected: Set<CashFlowCategoryKey>,
    onToggle: (CashFlowCategoryKey) -> Unit,
    onClear: () -> Unit,
) {
    if (categories.isEmpty()) return

    val allLabel = if (kind == CashFlowKind.EXPENSE) "Все категории" else "Все источники"
    var expanded by remember(kind) { mutableStateOf(false) }
    val selectedName = when (selected.size) {
        0 -> allLabel
        1 -> categories.first { it.key in selected }.name
        else -> categories.filter { it.key in selected }.joinToString(", ") { it.name }
    }

    Text(
        if (kind == CashFlowKind.EXPENSE) "Категории расходов" else "Источники доходов",
        style = TratyTheme.typography.labelMedium,
        color = TratyTheme.colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(TratyTheme.spacing.xxs))
    TratyDropdownPicker(
        expanded = expanded,
        onExpand = { expanded = true },
        onDismiss = { expanded = false },
        matchAnchorWidth = true,
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
        CashFlowCategoryOptions(
            allLabel = allLabel,
            categories = categories,
            selected = selected,
            onToggle = onToggle,
            onClear = onClear,
            onDone = { expanded = false },
        )
    }
    Spacer(Modifier.height(TratyTheme.spacing.md))
}

@Composable
private fun CashFlowCategoryOptions(
    allLabel: String,
    categories: List<CashFlowCategory>,
    selected: Set<CashFlowCategoryKey>,
    onToggle: (CashFlowCategoryKey) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val search = query.trim()
    val matches = remember(categories, search) {
        categories.filter { it.name.contains(search, ignoreCase = true) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(search) { scroll.scrollTo(0) }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Поиск по названию") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = TratyTheme.shapes.small,
        textStyle = TratyTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.md),
    )
    Spacer(Modifier.height(TratyTheme.spacing.xs))
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = TratyTheme.spacing.huge * 4)
            .verticalScroll(scroll),
    ) {
        if (search.isEmpty()) CategoryOption(allLabel, selected.isEmpty(), onClear)
        matches.forEach { category ->
            val isSelected = category.key in selected
            CategoryOption(
                category.name, isSelected,
                onClick = { onToggle(category.key) },
                enabled = isSelected || selected.size < MAX_COMPARED_CATEGORIES,
            )
        }
        if (matches.isEmpty()) {
            Text(
                "Ничего не найдено",
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(TratyTheme.spacing.lg),
            )
        }
    }
    if (selected.size == MAX_COMPARED_CATEGORIES) {
        Text(
            "Снимите отметку, чтобы выбрать другой пункт",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.xs),
        )
    }
    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
    TratyTextButton(onClick = onDone, modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm)) {
        Text("Готово")
    }
}

@Composable
private fun CategoryOption(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(name) },
        onClick = onClick,
        enabled = enabled,
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
