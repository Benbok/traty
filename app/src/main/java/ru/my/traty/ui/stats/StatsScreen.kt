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
import ru.my.traty.data.BreakdownGroup
import ru.my.traty.data.BreakdownMode
import ru.my.traty.data.CategoryBreakdown
import ru.my.traty.data.CategoryBreakdowns
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import ru.my.traty.data.StoreTotal
import ru.my.traty.data.period.SpendingPace
import ru.my.traty.data.period.SpendingPaces
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.charts.components.DateRangePickerModal
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyDeltaBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime
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
    val periodExpenses by vm.expenses.collectAsStateWithLifecycle()
    val customExpenses by rangeVm.expenses.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    var byStores by rememberSaveable { mutableStateOf(false) }
    // Раскрытые категории: под ними видно, из чего состоит категория
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    // Выбранный способ раскрытия для каждой категории (по умолчанию — CategoryBreakdown.defaultMode)
    var modes by remember { mutableStateOf(emptyMap<Long, BreakdownMode>()) }
    // Раскрытые подкатегории / магазины внутри категорий: «категория/способ/ключ»
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    // Режим «Даты» (range != null) не влияет на период ленты трат: он живёт только на этом экране
    val custom = range != null
    val totals = if (custom) customTotals else periodTotals
    val prev = if (custom) customPrev else periodPrev
    val prevMap = remember(prev) { prev.associate { it.categoryId to it.total } }
    val sum = totals.sumOf { it.total }
    val prevSum = prev.sumOf { it.total }
    val places = if (custom) customPlaces else periodPlaces
    val expenses = if (custom) customExpenses else periodExpenses
    val breakdowns = remember(expenses) { CategoryBreakdowns.build(expenses) }
    val stores = remember(places) { Places.group(places) }
    val emojiById = remember(totals) { totals.associate { it.categoryId to it.emoji } }

    // Средняя в день и прогноз на конец периода по текущему темпу (прогноз — пока период не кончился)
    val paceEnd = range?.second ?: period.endDate
    val pace = remember(sum, period, range) {
        val today = LocalDate.now()
        range?.let { SpendingPaces.of(it.first, it.second, today, sum) } ?: SpendingPaces.of(period, today, sum)
    }

    val deltaDescription = if (prevSum > 0) {
        val label = if (custom) "Предыдущие такие же даты" else "Прошлый период"
        "$label: ${formatRub(prevSum)} (${deltaText(sum, prevSum)})"
    } else null

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

        if (sum > 0 && pace != null) {
            item { SpendingPaceCard(pace = pace, endDate = paceEnd, prevSum = prevSum, custom = custom) }
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
            val breakdown = breakdowns[t.categoryId]
            val expandable = breakdown != null
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
                            contentDescription = if (isExpanded) "Скрыть состав категории" else "Показать состав категории",
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

                if (isExpanded && breakdown != null) {
                    val mode = modes[t.categoryId]?.takeIf { it in breakdown.modes } ?: breakdown.defaultMode
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    CategoryBreakdownView(
                        breakdown = breakdown,
                        mode = mode,
                        onMode = { modes = modes + (t.categoryId to it) },
                        isGroupExpanded = { g -> groupKey(t.categoryId, mode, g) in expandedGroups },
                        onToggleGroup = { g ->
                            val k = groupKey(t.categoryId, mode, g)
                            expandedGroups = if (k in expandedGroups) expandedGroups - k else expandedGroups + k
                        },
                    )
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

/**
 * «В среднем в день» и «Прогноз к концу периода». Прогноз линейный: сколько потрачено, плюс средняя в день
 * на каждый оставшийся день. Сравнивается с итогом прошлого периода, если он есть.
 */
@Composable
private fun SpendingPaceCard(pace: SpendingPace, endDate: LocalDate, prevSum: Long, custom: Boolean) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Row(Modifier.padding(TratyTheme.spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text("В среднем в день", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                TratyAmountText(amountKopecks = pace.dailyAverage, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
                Text(
                    text = "за ${days(pace.daysPassed)}",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            val forecast = pace.forecast
            if (forecast != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Прогноз к ${endDate.dayOfMonth} ${shortMonth(endDate)}",
                        style = TratyTheme.typography.labelMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    TratyAmountText(amountKopecks = forecast, size = AmountSize.MEDIUM, fontWeight = FontWeight.Bold)
                    val note = if (prevSum > 0) {
                        val diff = forecast - prevSum
                        val label = if (custom) "прошлых дат" else "прошлого периода"
                        when {
                            diff > 0 -> "на ${formatRub(diff)} больше $label"
                            diff < 0 -> "на ${formatRub(-diff)} меньше $label"
                            else -> "как в прошлый раз"
                        }
                    } else "осталось ${days(pace.daysLeft)}"
                    Text(
                        text = note,
                        style = TratyTheme.typography.bodySmall,
                        color = if (prevSum > 0 && forecast > prevSum) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val SHORT_MONTHS_GEN = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun shortMonth(d: LocalDate) = SHORT_MONTHS_GEN[d.monthValue - 1]

/** 1 день, 3 дня, 5 дней. */
internal fun days(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "дней"
        n % 10 == 1 -> "день"
        n % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "$n $word"
}

private fun groupKey(categoryId: Long, mode: BreakdownMode, g: BreakdownGroup) = "$categoryId/${mode.name}/${g.key}"

/** Сколько трат показывать в раскрытом списке, остальное — строкой «и ещё N». */
private const val EXPENSES_SHOWN = 30

/**
 * Состав раскрытой категории. Переключатель «Подкатегории / Магазины / Траты» показывает только те способы,
 * что имеют смысл: у «Авто» с бензином и мойкой есть подкатегории, у «Продуктов» — магазины.
 * Нажатие на подкатегорию или магазин открывает его траты.
 */
@Composable
private fun CategoryBreakdownView(
    breakdown: CategoryBreakdown,
    mode: BreakdownMode,
    onMode: (BreakdownMode) -> Unit,
    isGroupExpanded: (BreakdownGroup) -> Boolean,
    onToggleGroup: (BreakdownGroup) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 48.dp)) {
        if (breakdown.modes.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs),
            ) {
                breakdown.modes.forEach { m -> ModeChip(m.title, selected = m == mode, onClick = { onMode(m) }) }
            }
        }
        if (mode == BreakdownMode.EXPENSES) {
            ExpenseList(breakdown.expenses, mode)
        } else {
            breakdown.groups(mode).forEach { g ->
                val open = isGroupExpanded(g)
                BreakdownGroupRow(group = g, categoryTotal = breakdown.total, expanded = open, onClick = { onToggleGroup(g) })
                if (open) Column(Modifier.padding(start = TratyTheme.spacing.md)) { ExpenseList(g.expenses, mode) }
            }
        }
    }
}

/** Подкатегория или магазин внутри раскрытой категории: название, число трат, сумма и доля в категории. */
@Composable
private fun BreakdownGroupRow(group: BreakdownGroup, categoryTotal: Long, expanded: Boolean, onClick: () -> Unit) {
    val frac = if (categoryTotal > 0) group.total.toFloat() / categoryTotal else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = TratyTheme.spacing.xxs, bottom = TratyTheme.spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = TratyTheme.typography.bodyMedium,
                    color = if (group.isUnassigned) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
                )
                Text(
                    text = "${purchases(group.count)} · ${(frac * 100).roundToInt()}%",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            TratyAmountText(amountKopecks = group.total, size = AmountSize.SMALL, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Скрыть траты" else "Показать траты",
                tint = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = TratyTheme.spacing.xs).size(18.dp),
            )
        }
        TratyProgressBar(progress = frac, height = 4.dp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Траты списком: что купили, когда и где, сумма. */
@Composable
private fun ExpenseList(list: List<ExpenseWithCategory>, mode: BreakdownMode) {
    list.take(EXPENSES_SHOWN).forEach { e -> BreakdownExpenseRow(e, showPlace = mode != BreakdownMode.STORES) }
    if (list.size > EXPENSES_SHOWN) {
        Text(
            text = "и ещё ${purchases(list.size - EXPENSES_SHOWN)} — все траты на главной",
            style = TratyTheme.typography.labelSmall,
            color = TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = TratyTheme.spacing.xxs),
        )
    }
}

@Composable
private fun BreakdownExpenseRow(e: ExpenseWithCategory, showPlace: Boolean) {
    val title = CategoryBreakdowns.title(e).ifEmpty { "Без описания" }
    val place = Places.normalize(e.expense.place)?.takeIf { showPlace && it != title }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurface, maxLines = 1)
            Text(
                text = listOfNotNull(formatTime(e.expense.timestamp), place).joinToString(" · "),
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TratyAmountText(amountKopecks = e.expense.amountKopecks, size = AmountSize.SMALL)
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
