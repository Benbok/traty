package ru.my.traty.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.Places
import ru.my.traty.data.StoreTotal
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.charts.components.DateRangePickerModal
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyDeltaBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Экран статистики по категориям трат и комментария ИИ.
 */
@Composable
fun StatsScreen(vm: MainViewModel, rangeVm: StatsRangeViewModel = viewModel()) {
    val period by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val periodTotals by vm.totals.collectAsStateWithLifecycle()
    val periodPrev by vm.prevTotals.collectAsStateWithLifecycle()
    val range by rangeVm.range.collectAsStateWithLifecycle()
    val customTotals by rangeVm.totals.collectAsStateWithLifecycle()
    val customPrev by rangeVm.prevTotals.collectAsStateWithLifecycle()
    val periodPlaces by vm.placeTotals.collectAsStateWithLifecycle()
    val customPlaces by rangeVm.placeTotals.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    var byStores by rememberSaveable { mutableStateOf(false) }
    // Раскрытые категории: под ними видна разбивка по магазинам
    var expanded by remember { mutableStateOf(emptySet<Long>()) }

    // Режим «Даты» (range != null) не влияет на период ленты трат: он живёт только на этом экране
    val custom = range != null
    val totals = if (custom) customTotals else periodTotals
    val prev = if (custom) customPrev else periodPrev
    val prevMap = remember(prev) { prev.associate { it.categoryId to it.total } }
    val sum = totals.sumOf { it.total }
    val prevSum = prev.sumOf { it.total }
    val places = if (custom) customPlaces else periodPlaces
    val storesByCategory = remember(places) { Places.byCategory(places) }
    val stores = remember(places) { Places.group(places) }
    val emojiById = remember(totals) { totals.associate { it.categoryId to it.emoji } }

    val deltaDescription = if (prevSum > 0) {
        val label = if (custom) "Предыдущие такие же даты" else "Прошлый период"
        "$label: ${formatRub(prevSum)} (${deltaText(sum, prevSum)})"
    } else null

    Column(Modifier.fillMaxSize()) {
        TratyHeaderCard(
            title = "Статистика",
            subtitle = if (custom) "Выбран диапазон дат" else "Структура расходов по категориям",
            icon = Icons.Filled.PieChart,
            actions = {
                TratyIconButton(
                    icon = Icons.Filled.DateRange,
                    contentDescription = "Выбрать диапазон",
                    onClick = { showRangePicker = true },
                    containerColor = if (custom) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                    tint = if (custom) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                StatsModeChips(
                customSelected = custom,
                onPeriod = rangeVm::clear,
                onDates = { showRangePicker = true },
            )
        }

        item {
            TratyMonthPicker(
                periodLabel = range?.let { "${formatDate(it.first)} — ${formatDate(it.second)}" }
                    ?: formatPeriod(period, settings.periodStartDay),
                totalKopecks = sum,
                onPrev = if (custom) { { rangeVm.shift(-1) } } else vm::prevBillingPeriod,
                onNext = if (custom) { { rangeVm.shift(1) } } else vm::nextBillingPeriod,
                deltaText = deltaDescription,
                // В режиме «Даты» нажатие на подпись открывает календарь выбора диапазона
                onDateClick = if (custom) { { showRangePicker = true } } else null,
            )
        }

        if (totals.isNotEmpty()) {
            item {
                StatsViewChips(
                    storesSelected = byStores,
                    onCategories = { byStores = false },
                    onStores = { byStores = true },
                )
            }
        }

        if (totals.isEmpty()) {
            item {
                TratyEmptyState(
                    icon = Icons.Filled.PieChart,
                    title = "Нет статистики",
                    description = if (custom) {
                        "В выбранных датах трат нет."
                    } else {
                        "В этом периоде пока нет зарегистрированных трат."
                    },
                )
            }
        }

        if (byStores) items(stores, key = { "store:" + it.key }) { st ->
            StoreTotalRow(store = st, sum = sum, emojiById = emojiById)
        }

        if (!byStores) items(totals, key = { it.categoryId }) { t ->
            val frac = if (sum > 0) t.total.toFloat() / sum else 0f
            val p = prevMap[t.categoryId]
            val catStores = storesByCategory[t.categoryId].orEmpty()
            val expandable = Places.hasNamed(catStores)
            val isExpanded = expandable && t.categoryId in expanded

            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (expandable) Modifier.clickable {
                            expanded = if (t.categoryId in expanded) expanded - t.categoryId else expanded + t.categoryId
                        } else Modifier
                    )
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TratyCategoryBadge(
                        emoji = t.emoji,
                        categoryName = t.name,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.md))
                    Text(
                        text = t.name,
                        style = TratyTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TratyAmountText(
                        amountKopecks = t.total,
                        size = AmountSize.MEDIUM,
                        fontWeight = FontWeight.Bold,
                    )
                    if (expandable) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Скрыть магазины" else "Показать магазины",
                            tint = TratyTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = TratyTheme.spacing.xs).size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                TratyProgressBar(
                    progress = frac,
                    modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${(frac * 100).roundToInt()}% от всех трат",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    if (p != null) {
                        TratyDeltaBadge(current = t.total, previous = p)
                    } else {
                        Text(
                            text = "новая категория",
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.info,
                        )
                    }
                }

                if (isExpanded) {
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    catStores.forEach { st -> CategoryStoreRow(store = st, categoryTotal = t.total) }
                }
            }
        }

        // Комментарий ИИ считается по расчётному периоду, поэтому в режиме «Даты» его не показываем
        if (!custom) item {
            TratyCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TratyTheme.spacing.lg),
                backgroundColor = TratyTheme.colors.surfaceContainer,
            ) {
                Column(Modifier.padding(TratyTheme.spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = TratyTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(TratyTheme.spacing.sm))
                        Text("Комментарий ИИ", style = TratyTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(TratyTheme.spacing.xs))

                    Text(
                        text = "Отправляются только суммы по категориям — без мест и заметок.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(TratyTheme.spacing.md))

                    vm.insight?.let {
                        Text(
                            text = it,
                            style = TratyTheme.typography.bodyMedium,
                            color = TratyTheme.colors.onSurface,
                        )
                        Spacer(Modifier.height(TratyTheme.spacing.md))
                    }

                    if (vm.insightBusy) {
                        CircularProgressIndicator(
                            color = TratyTheme.colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        TratyButton(onClick = vm::requestInsight) {
                            Text(if (vm.insight == null) "Получить анализ" else "Обновить анализ")
                        }
                    }
                }
            }
        }
    }
}

    if (showRangePicker) {
        StatsRangeDialog(
            range = range,
            onSelected = rangeVm::setRange,
            onDismiss = { showRangePicker = false },
        )
    }
}

@Composable
private fun StatsRangeDialog(
    range: Pair<LocalDate, LocalDate>?,
    onSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    DateRangePickerModal(
        initialStart = range?.first ?: today.minusDays(29),
        initialEnd = range?.second ?: today,
        onRangeSelected = onSelected,
        onDismiss = onDismiss,
    )
}

/** Магазин внутри раскрытой категории: название, сумма и доля в категории. */
@Composable
private fun CategoryStoreRow(store: StoreTotal, categoryTotal: Long) {
    val frac = if (categoryTotal > 0) store.totalKopecks.toFloat() / categoryTotal else 0f
    Column(Modifier.fillMaxWidth().padding(start = 48.dp, top = TratyTheme.spacing.xxs, bottom = TratyTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = store.name,
                style = TratyTheme.typography.bodyMedium,
                color = if (store.isNoPlace) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(frac * 100).roundToInt()}%",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(TratyTheme.spacing.sm))
            TratyAmountText(amountKopecks = store.totalKopecks, size = AmountSize.SMALL, fontWeight = FontWeight.SemiBold)
        }
        TratyProgressBar(progress = frac, height = 4.dp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Строка режима «Магазины»: магазин, в каких категориях тратили, сколько покупок, сумма и доля. */
@Composable
private fun StoreTotalRow(store: StoreTotal, sum: Long, emojiById: Map<Long, String>) {
    val frac = if (sum > 0) store.totalKopecks.toFloat() / sum else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = if (store.isNoPlace) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(TratyTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(text = store.name, style = TratyTheme.typography.titleSmall)
                val cats = store.categoryIds.mapNotNull { emojiById[it] }.joinToString(" ")
                Text(
                    text = listOf(cats, purchases(store.count)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            TratyAmountText(amountKopecks = store.totalKopecks, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(TratyTheme.spacing.xs))
        TratyProgressBar(progress = frac, modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs))
        Text(
            text = "${(frac * 100).roundToInt()}% от всех трат",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

/** 1 покупка, 3 покупки, 5 покупок. */
internal fun purchases(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    val word = when {
        m100 in 11..14 -> "покупок"
        m10 == 1 -> "покупка"
        m10 in 2..4 -> "покупки"
        else -> "покупок"
    }
    return "$n $word"
}

private fun deltaText(cur: Long, prev: Long): String {
    if (prev == 0L) return "—"
    val pct = ((cur - prev) * 100.0 / prev).roundToInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
}
