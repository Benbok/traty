package ru.my.traty.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.items.ItemsState
import ru.my.traty.ui.items.ItemsViewModel
import ru.my.traty.ui.items.ProductRow
import java.time.LocalDate

private const val TOP_PRODUCTS = 3

/** Карточка «Товары» в «Статистике»: на что ушло больше всего за те же даты, что и вся статистика. */
internal fun LazyListScope.itemsOverviewItem(vm: ItemsViewModel, from: LocalDate, to: LocalDate, onOpen: () -> Unit) {
    item(key = "items", contentType = "items") {
        LaunchedEffect(from, to) { vm.setWindow(from, to) }
        val state by vm.state.collectAsStateWithLifecycle()
        ItemsOverviewCard(state, onOpen)
    }
}

@Composable
private fun ItemsOverviewCard(state: ItemsState, onOpen: () -> Unit) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(vertical = TratyTheme.spacing.md)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Товары", style = TratyTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.products.isNotEmpty()) {
                        Text(
                            "${state.products.size} ${productsWord(state.products.size)} на ${formatRub(state.products.sumOf { it.totalKopecks })}",
                            style = TratyTheme.typography.bodySmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                TratyTextButton(onClick = onOpen) { Text("Все", style = TratyTheme.typography.labelMedium) }
            }
            if (state.products.isEmpty()) {
                Text(
                    "В эти дни товары не записаны. Скажите, что купили и почём: «Эльдорадо, телефон 40 тысяч, чехол 2 тысячи», " +
                        "и здесь будет видно, сколько ушло на каждый товар.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.md),
                )
                return@Column
            }
            val max = state.products.first().totalKopecks
            state.products.take(TOP_PRODUCTS).forEach { p -> ProductRow(p, max, showPlaces = true, onClick = onOpen) }
        }
    }
}
