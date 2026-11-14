package ru.my.traty.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import ru.my.traty.data.CategoryChange
import ru.my.traty.data.PeriodComparison
import ru.my.traty.data.RingSlice
import ru.my.traty.data.RingSlices
import ru.my.traty.data.period.CumulativeLine
import ru.my.traty.data.period.HeatCalendar
import ru.my.traty.data.period.HeatDay
import ru.my.traty.data.period.HeatMonth
import ru.my.traty.data.period.CumulativeSpending
import ru.my.traty.data.period.PaceVsPrevious
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
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatRubSigned
import ru.my.traty.ui.formatDateWithWeekday
import ru.my.traty.ui.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val periodComparable by vm.comparableTotals.collectAsStateWithLifecycle()
    val comparableWindow by vm.comparableWindow.collectAsStateWithLifecycle()
    val range by rangeVm.range.collectAsStateWithLifecycle()
    val customTotals by rangeVm.totals.collectAsStateWithLifecycle()
    val customPrev by rangeVm.prevTotals.collectAsStateWithLifecycle()
    val periodPlaces by vm.placeTotals.collectAsStateWithLifecycle()
    val customPlaces by rangeVm.placeTotals.collectAsStateWithLifecycle()
    val periodExpenses by vm.expenses.collectAsStateWithLifecycle()
    val customExpenses by rangeVm.expenses.collectAsStateWithLifecycle()
    val prevExpenses by vm.prevExpenses.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    var byStores by rememberSaveable { mutableStateOf(false) }
    // Раскрытые категории: под ними видно, из чего состоит категория
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    // Выбранный способ раскрытия для каждой категории (по умолчанию — CategoryBreakdown.defaultMode)
    var modes by remember { mutableStateOf(emptyMap<Long, BreakdownMode>()) }
    // Раскрытые подкатегории / магазины внутри категорий: «категория/способ/ключ»
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }
    // День, нажатый в тепловом календаре: под календарём видны его траты
    var heatSelected by remember { mutableStateOf<LocalDate?>(null) }

    // Режим «Даты» (range != null) не влияет на период ленты трат: он живёт только на этом экране
    val custom = range != null
    val totals = if (custom) customTotals else periodTotals
    val prev = if (custom) customPrev else periodPrev
    // Категории сравниваем с теми же днями прошлого периода (в режиме «Даты» прошлый отрезок и так той же длины)
    val comparable = if (custom) customPrev else periodComparable
    val prevMap = remember(comparable) { comparable.associate { it.categoryId to it.total } }
    val topChanges = remember(totals, comparable) { PeriodComparison.top(totals, comparable) }
    val compareLabel = when {
        custom -> "с предыдущими такими же датами"
        else -> "с ${formatDateSpan(comparableWindow.first, comparableWindow.second)}"
    }
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

    // Линия накопленных трат: текущий расчётный период против прошлого (в режиме «Даты» не показываем)
    val cumulative = remember(periodExpenses, prevExpenses, period, settings.periodStartDay) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun spends(list: List<ExpenseWithCategory>) = list.map {
            Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() to it.expense.amountKopecks
        }
        val prevPeriod = period.previous(settings.periodStartDay)
        CumulativeSpending.line(period.startDate, period.endDate, today, spends(periodExpenses)) to
            CumulativeSpending.line(prevPeriod.startDate, prevPeriod.endDate, today, spends(prevExpenses))
    }

    // Тепловой календарь по дням текущего отрезка (расчётный период или «Даты» до 62 дней)
    val heatStart = range?.first ?: period.startDate
    val heatEnd = range?.second ?: period.endDate
    val heat = remember(expenses, heatStart, heatEnd) {
        val zone = ZoneId.systemDefault()
        HeatCalendar.build(
            heatStart, heatEnd,
            expenses.map { Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() to it.expense.amountKopecks },
        )
    }

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

        val ringSlices = remember(totals) { RingSlices.of(totals) }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "mode-chips", contentType = "mode-chips") {
                StatsModeChips(
                customSelected = custom,
                onPeriod = rangeVm::clear,
                onDates = { showRangePicker = true },
            )
        }

        item(key = "month-picker", contentType = "month-picker") {
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
            item(key = "pace", contentType = "pace") { SpendingPaceCard(pace = pace, endDate = paceEnd, prevSum = prevSum, custom = custom) }
        }

        if (!custom && cumulative.first.points.isNotEmpty() && cumulative.second.last > 0) {
            item(key = "cumulative", contentType = "cumulative") { CumulativeCard(current = cumulative.first, previous = cumulative.second) }
        }

        if (sum > 0 && heat != null) {
            item {
                val selected = heatSelected?.takeIf { it in heatStart..heatEnd }
                val zone = ZoneId.systemDefault()
                val dayExpenses = remember(expenses, selected) {
                    if (selected == null) emptyList()
                    else expenses.filter { Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() == selected }
                }
                HeatCalendarCard(
                    month = heat,
                    selected = selected,
                    dayExpenses = dayExpenses,
                    onDay = { d -> heatSelected = if (heatSelected == d) null else d },
                )
            }
        }

        if (topChanges.isNotEmpty() && comparable.isNotEmpty()) {
            item(key = "top-changes", contentType = "top-changes") { TopChangesCard(changes = topChanges, compareLabel = compareLabel) }
        }

        if (totals.isNotEmpty()) {
            item(key = "ring", contentType = "ring") { CategoryRingCard(slices = ringSlices, sum = sum) }
        }

        if (totals.isNotEmpty()) {
            item(key = "view-chips", contentType = "view-chips") {
                StatsViewChips(
                    storesSelected = byStores,
                    onCategories = { byStores = false },
                    onStores = { byStores = true },
                )
            }
        }

        if (totals.isEmpty()) {
            item(key = "empty", contentType = "empty") {
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

        if (byStores) items(stores, key = { "store:" + it.key }, contentType = { "store" }) { st ->
            StoreTotalRow(store = st, sum = sum, emojiById = emojiById)
        }

        if (!byStores) items(totals, key = { "cat:${it.categoryId}" }, contentType = { "category" }) { t ->
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (t.total != p) {
                                Text(
                                    text = formatRubSigned(t.total - p),
                                    style = TratyTheme.typography.labelSmall,
                                    color = TratyTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.padding(end = TratyTheme.spacing.xs),
                                )
                            }
                            TratyDeltaBadge(current = t.total, previous = p)
                        }
                    } else {
                        Text(
                            text = "раньше не было",
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

/**
 * Накопленные траты по дням: сплошная линия — этот период (до сегодня), пунктир — прошлый целиком.
 * Подпись сверху — обгоняю или отстаю от прошлого периода на этот же день.
 */
@Composable
private fun CumulativeCard(current: CumulativeLine, previous: CumulativeLine) {
    val vs: PaceVsPrevious? = CumulativeSpending.vsPrevious(current, previous)
    val curColor = TratyTheme.colors.primary
    val prevColor = TratyTheme.colors.onSurfaceVariant.copy(alpha = 0.6f)
    val gridColor = TratyTheme.colors.surfaceContainerHigh
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Накопленные траты", style = TratyTheme.typography.titleSmall)
            if (vs != null) {
                val text = when {
                    vs.diff > 0 -> "Обгоняю прошлый период на ${formatRub(vs.diff)} к ${vs.day}-му дню"
                    vs.diff < 0 -> "Отстаю от прошлого периода на ${formatRub(-vs.diff)} к ${vs.day}-му дню"
                    else -> "Иду вровень с прошлым периодом"
                }
                Text(
                    text = text,
                    style = TratyTheme.typography.bodySmall,
                    color = if (vs.diff > 0) TratyTheme.colors.expense else TratyTheme.colors.income,
                )
            }
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            val days = maxOf(current.daysTotal, previous.daysTotal)
            val maxY = maxOf(current.last, previous.last).coerceAtLeast(1)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val w = size.width
                val h = size.height
                fun x(i: Int) = if (days <= 1) 0f else w * i / (days - 1)
                fun y(v: Long) = h - h * (v.toFloat() / maxY)
                for (k in 0..2) {
                    val gy = h * k / 2
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }
                fun path(points: List<Long>) = Path().apply {
                    points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(
                    path(previous.points),
                    prevColor,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
                drawPath(path(current.points), curColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                current.points.lastOrNull()?.let {
                    drawCircle(curColor, radius = 4.dp.toPx(), center = Offset(x(current.points.lastIndex), y(it)))
                }
            }
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(curColor)
                Text(
                    "Сейчас: ${formatRub(current.last)}",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                LegendDot(prevColor)
                Text(
                    "Прошлый: ${formatRub(previous.last)}",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(Modifier.padding(end = 4.dp).size(8.dp)) { drawCircle(color) }
}

/** Цвета долей кольца: насыщенные, различимые в светлой и тёмной теме; «Остальное» — серое. */
private val RING_COLORS = listOf(
    Color(0xFF4F7CF7), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF06B6D4),
)
private val RING_REST = Color(0xFF94A3B8)

/** Кольцевая диаграмма по категориям: крупные категории своими долями, мелкие — «Остальное». */
@Composable
private fun CategoryRingCard(slices: List<RingSlice>, sum: Long) {
    if (slices.isEmpty()) return
    val colors = slices.mapIndexed { i, sl -> if (sl.categoryId == null) RING_REST else RING_COLORS[i % RING_COLORS.size] }
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Row(Modifier.padding(TratyTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 18.dp.toPx()
                    val gap = if (slices.size > 1) 1.5f else 0f
                    var startAngle = -90f
                    slices.forEachIndexed { i, sl ->
                        val sweep = 360f * sl.fraction
                        drawArc(
                            color = colors[i],
                            startAngle = startAngle + gap / 2,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke),
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Всего", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                    Text(formatRub(sum), style = TratyTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(TratyTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                slices.forEachIndexed { i, sl ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(colors[i])
                        Text(
                            text = listOf(sl.emoji, sl.name).filter { it.isNotBlank() }.joinToString(" "),
                            style = TratyTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(sl.fraction * 100).roundToInt()}%",
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Тепловой календарь: чем больше потрачено за день, тем насыщеннее клетка (4 уровня от самого дорогого дня).
 * Нажатие на день показывает его траты под календарём, повторное — скрывает.
 */
@Composable
private fun HeatCalendarCard(
    month: HeatMonth,
    selected: LocalDate?,
    dayExpenses: List<ExpenseWithCategory>,
    onDay: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val base = TratyTheme.colors.expense
    val empty = TratyTheme.colors.surfaceContainerHigh
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Календарь трат", style = TratyTheme.typography.titleSmall)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach {
                    Text(
                        text = it,
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val cells: List<HeatDay?> = List(month.leadingBlanks) { null } + month.days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val day = week.getOrNull(col)
                        Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f)) {
                            if (day != null) {
                                val color = if (day.level == 0) empty else base.copy(alpha = 0.18f + 0.82f * day.level / HeatCalendar.LEVELS)
                                val isSel = day.date == selected
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(TratyTheme.shapes.extraSmall)
                                        .background(color)
                                        .then(
                                            if (isSel || day.date == today) {
                                                Modifier.border(
                                                    BorderStroke(if (isSel) 2.dp else 1.dp, TratyTheme.colors.onSurface),
                                                    TratyTheme.shapes.extraSmall,
                                                )
                                            } else Modifier
                                        )
                                        .clickable { onDay(day.date) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.date.dayOfMonth.toString(),
                                        style = TratyTheme.typography.labelSmall,
                                        color = if (day.level >= 3) Color.White else TratyTheme.colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (selected != null) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                val total = dayExpenses.sumOf { it.expense.amountKopecks }
                Text(
                    text = "${formatDateWithWeekday(selected)} · ${formatRub(total)}",
                    style = TratyTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (dayExpenses.isEmpty()) {
                    Text("В этот день трат нет", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                } else {
                    dayExpenses.sortedByDescending { it.expense.amountKopecks }.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.categoryEmoji, modifier = Modifier.padding(end = TratyTheme.spacing.xs))
                            BreakdownExpenseRow(e, showPlace = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** «Что изменилось»: три категории с самой большой разницей в рублях, рост — красным, падение — зелёным. */
@Composable
private fun TopChangesCard(changes: List<CategoryChange>, compareLabel: String) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Что изменилось", style = TratyTheme.typography.titleSmall)
            Text(
                text = "по сравнению $compareLabel",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            changes.forEach { ch ->
                val up = ch.diff > 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${ch.emoji} ${ch.name}",
                        style = TratyTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    val pct = ch.percent?.let { if (it > 0) " (+$it%)" else " ($it%)" } ?: " (новая)"
                    Text(
                        text = formatRubSigned(ch.diff) + pct,
                        style = TratyTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (up) TratyTheme.colors.expense else TratyTheme.colors.income,
                    )
                }
            }
        }
    }
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
private fun BreakdownExpenseRow(e: ExpenseWithCategory, showPlace: Boolean, modifier: Modifier = Modifier) {
    val title = CategoryBreakdowns.title(e).ifEmpty { "Без описания" }
    val place = Places.normalize(e.expense.place)?.takeIf { showPlace && it != title }
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
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
