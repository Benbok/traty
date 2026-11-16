package ru.my.traty.ui.items

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.PaddingValues
import ru.my.traty.data.items.ItemTotals
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.data.items.StoreItems
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.stats.ModeChip
import java.time.LocalDate

/**
 * «Товары»: сколько ушло на каждый товар за даты из «Статистики», по всем магазинам или внутри одного.
 * Нажатие на товар показывает, где и почём его покупали.
 */
@Composable
fun ItemsScreen(from: LocalDate, to: LocalDate, onBack: () -> Unit, vm: ItemsViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    LaunchedEffect(from, to) { vm.setWindow(from, to) }
    val state by vm.state.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<ProductTotal?>(null) }
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Товары",
                subtitle = "${formatDateSpan(from, to)} · ${formatRub(state.products.sumOf { it.totalKopecks })}",
                onBack = onBack,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize(),
            contentPadding = PaddingValues(vertical = TratyTheme.spacing.sm),
        ) {
            if (state.stores.size > 1 || state.store != null) {
                item(key = "stores") { StoreChips(state.stores, state.store, vm::selectStore) }
            }
            if (state.products.isEmpty()) {
                item(key = "empty") {
                    TratyEmptyState(
                        icon = Icons.Filled.ShoppingCart,
                        title = "Товаров пока нет",
                        description = "Продиктуйте покупку с товарами, например: «Эльдорадо 45 тысяч, телефон 40 тысяч, чехол 2 тысячи, " +
                            "клавиатура 3 тысячи». Товары можно добавить и на экране траты.",
                    )
                }
            }
            val max = state.products.maxOfOrNull { it.totalKopecks } ?: 0L
            items(state.products, key = { it.key }) { p ->
                ProductRow(p, max, showPlaces = state.store == null) { opened = p }
            }
        }
    }
    opened?.let { p ->
        val history = remember(p, state.sales, state.store) {
            ItemTotals.history(state.sales, p.key).filter { s ->
                state.store == null || ru.my.traty.data.Places.key(s.place) == ru.my.traty.data.Places.key(state.store)
            }
        }
        ItemHistorySheet(p, history, onDismiss = { opened = null })
    }
}

/** «Все магазины», «Эльдорадо», «Пятёрочка»: товары внутри одного магазина. */
@Composable
private fun StoreChips(stores: List<StoreItems>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        ModeChip("Все магазины", selected = selected == null, onClick = { onSelect(null) })
        stores.forEach { s ->
            ModeChip(s.name, selected = selected == s.name, onClick = { onSelect(s.name) })
        }
    }
}
