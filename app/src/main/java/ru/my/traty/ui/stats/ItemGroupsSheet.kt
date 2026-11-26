package ru.my.traty.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.my.traty.data.items.ItemGroup
import ru.my.traty.data.items.ItemGroups
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyMaterialIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemGroupsSheet(
    state: ItemGroupsSheetState,
    onQueryChange: (String) -> Unit,
    onSelectGroup: (ItemGroup) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = remember(state.groups, state.query) { ItemGroups.filter(state.groups, state.query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            val selected = state.selectedGroup
            if (selected != null) {
                ItemGroupDetailContent(
                    group = selected,
                    periodLabel = state.periodLabel,
                    onBack = onClearSelection,
                )
            } else {
                SheetHeader(state)
                if (!state.loading && state.groups.size > 1) {
                    ItemSearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
                    )
                }
                SheetBody(state = state, visible = visible, onSelectGroup = onSelectGroup)
            }
        }
    }
}

@Composable
private fun SheetHeader(state: ItemGroupsSheetState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(emoji = state.categoryEmoji, categoryName = state.categoryName, size = 36.dp)
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Товары · ${state.categoryName}",
                style = TratyTheme.typography.titleMedium,
            )
            val countSuffix = if (!state.loading && state.error == null && state.groups.isNotEmpty()) {
                " · ${state.groups.size} ${productsWord(state.groups.size)}"
            } else ""
            Text(
                text = state.periodLabel + countSuffix,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Поиск по товарам и магазинам…", style = TratyTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TratyMaterialIconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Очистить", modifier = Modifier.size(20.dp))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = TratyTheme.shapes.small,
        textStyle = TratyTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SheetBody(
    state: ItemGroupsSheetState,
    visible: List<ItemGroup>,
    onSelectGroup: (ItemGroup) -> Unit,
) {
    when {
        state.loading -> Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = TratyTheme.colors.primary)
        }
        state.error != null -> TratyEmptyState(
            icon = Icons.Filled.ShoppingBag,
            title = "Не получилось открыть",
            description = state.error,
        )
        state.groups.isEmpty() -> TratyEmptyState(
            icon = Icons.Filled.ShoppingBag,
            title = "Нет товаров",
            description = "В этой категории за выбранный период нет позиций чеков.",
        )
        visible.isEmpty() -> TratyEmptyState(
            icon = Icons.Filled.SearchOff,
            title = "Ничего не нашлось",
            description = "По запросу «${state.query.trim()}» товаров нет.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(
                start = TratyTheme.spacing.lg,
                end = TratyTheme.spacing.lg,
                top = TratyTheme.spacing.xs,
                bottom = TratyTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            items(visible, key = { it.displayName + ":" + it.variants.size }) { group ->
                ItemGroupCard(group = group, onClick = { onSelectGroup(group) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemGroupCard(group: ItemGroup, onClick: () -> Unit) {
    TratyCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surfaceContainer,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = group.displayName,
                    style = TratyTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TratyTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text(
                    text = timesWord(group.count),
                    style = TratyTheme.typography.labelMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            if (group.places.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    group.places.forEach { place -> StoreChip(place) }
                }
            }
            PriceRangeRow(group)
        }
    }
}

@Composable
internal fun StoreChip(name: String) {
    Box(
        modifier = Modifier
            .clip(TratyTheme.shapes.extraSmall)
            .background(TratyTheme.colors.surfaceContainerHigh)
            .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xxs),
    ) {
        Text(
            text = name,
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PriceRangeRow(group: ItemGroup) {
    val min = group.minPriceKopecks
    val max = group.maxPriceKopecks
    val unitSuffix = group.priceUnit?.let { "/$it" }.orEmpty()
    when {
        min == null || max == null -> Text(
            text = "Цена не указана",
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        min == max -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatRub(min) + unitSuffix,
                style = TratyTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TratyTheme.colors.onSurface,
            )
            if (group.count > 1) {
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text(
                    text = "цена не менялась",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${formatRub(min)}$unitSuffix — ${formatRub(max)}$unitSuffix",
                style = TratyTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TratyTheme.colors.onSurface,
            )
            if (min > 0L) {
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                val pct = ((max - min) * 100.0 / min).roundToInt()
                Box(
                    modifier = Modifier
                        .clip(TratyTheme.shapes.extraSmall)
                        .background(TratyTheme.colors.expenseContainer)
                        .padding(horizontal = TratyTheme.spacing.xs, vertical = TratyTheme.spacing.xxs),
                ) {
                    Text(
                        text = "+$pct%",
                        style = TratyTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TratyTheme.colors.expense,
                    )
                }
            }
        }
    }
}

internal fun timesWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    val word = when {
        m100 in 11..14 -> "раз"
        m10 == 1 -> "раз"
        m10 in 2..4 -> "раза"
        else -> "раз"
    }
    return "$n $word"
}
