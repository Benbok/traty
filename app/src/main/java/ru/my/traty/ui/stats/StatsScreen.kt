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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ru.my.traty.data.BreakdownMode
import ru.my.traty.data.CategoryBreakdowns
import ru.my.traty.data.ExpenseWithCategory
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
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.ui.budget.PlanSummaryCard
import ru.my.traty.ui.budget.planColor
import ru.my.traty.ui.budget.planLineText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Экран статистики по категориям трат и комментария ИИ.
 */
@Composable
fun StatsScreen(
    vm: MainViewModel,
    rangeVm: StatsRangeViewModel = viewModel(),
    debtsVm: StatsDebtsViewModel = viewModel(),
    onOpenBudget: () -> Unit = {},
    onOpenDebts: () -> Unit = {},
    onOpenDebt: (Long) -> Unit = {},
) {
    val period by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val reportingPeriod = remember(period, settings.periodStartDay) {
        rangeVm.observePeriod(period, settings.periodStartDay)
    }
    val periodReport by reportingPeriod.collectAsStateWithLifecycle(initialValue = StatsPeriodReport())
    val periodTotals = periodReport.totals
    val periodPrev = periodReport.previousTotals
    val periodComparable = periodReport.comparableTotals
    val comparableWindow = periodReport.comparableWindow
    val range by rangeVm.range.collectAsStateWithLifecycle()
    val customTotals by rangeVm.totals.collectAsStateWithLifecycle()
    val customPrev by rangeVm.prevTotals.collectAsStateWithLifecycle()
    val periodExpenses = periodReport.expenses
    val customExpenses by rangeVm.expenses.collectAsStateWithLifecycle()
    val prevExpenses = periodReport.previousExpenses
    val plans by vm.plans.collectAsStateWithLifecycle()
    val totalLimit by vm.totalLimit.collectAsStateWithLifecycle()
    // План категории целиком — для строки «План … · осталось …» под категорией
    val categoryPlans = remember(plans) {
        plans.filter { it.progress.plan.subcategoryId == null }.associateBy { it.progress.plan.categoryId }
    }
    var showRangePicker by remember { mutableStateOf(false) }
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
        spanOf(range) == StatsSpan.DAY -> "с предыдущим днём"
        spanOf(range) == StatsSpan.WEEK -> "с прошлой неделей"
        custom -> "с предыдущими такими же датами"
        else -> "с ${formatDateSpan(comparableWindow.first, comparableWindow.second)}"
    }
    val sum = totals.sumOf { it.total }
    val prevSum = prev.sumOf { it.total }
    val expenses = if (custom) customExpenses else periodExpenses
    val breakdowns = remember(expenses) { CategoryBreakdowns.build(expenses) }

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

    val deltaDescription = deltaDescriptionText(spanOf(range), sum, prevSum)

    Column(Modifier.fillMaxSize()) {
        TratyHeaderCard(
            title = "Статистика",
            subtitle = when (spanOf(range)) {
                StatsSpan.PERIOD -> "Структура расходов по категориям"
                StatsSpan.DAY -> "Траты за день"
                StatsSpan.WEEK -> "Траты за неделю"
                StatsSpan.DATES -> "Выбран диапазон дат"
            },
            icon = Icons.Filled.PieChart,
        )

        val ringSlices = remember(totals) { RingSlices.of(totals) }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "mode-chips", contentType = "mode-chips") {
                StatsModeChips(
                    selected = spanOf(range),
                    onSelect = { span ->
                        // День и неделя берутся вокруг сегодняшнего дня; стрелки потом листают по дню / неделе
                        val today = LocalDate.now()
                        when (span) {
                            StatsSpan.PERIOD -> rangeVm.clear()
                            StatsSpan.DAY -> dayRange(today).let { rangeVm.setRange(it.first, it.second) }
                            StatsSpan.WEEK -> weekRange(today).let { rangeVm.setRange(it.first, it.second) }
                            StatsSpan.DATES -> showRangePicker = true
                        }
                    },
                )
            }

        item(key = "month-picker", contentType = "month-picker") {
            TratyMonthPicker(
                periodLabel = range?.let {
                    when {
                        it.first == it.second -> formatDateWithWeekday(it.first)
                        spanOf(it) == StatsSpan.WEEK -> "Неделя: ${formatDateSpan(it.first, it.second)}"
                        else -> "${formatDate(it.first)} — ${formatDate(it.second)}"
                    }
                }
                    ?: formatPeriod(period, settings.periodStartDay),
                totalKopecks = sum,
                onPrev = if (custom) { { rangeVm.shift(-1) } } else vm::prevBillingPeriod,
                onNext = if (custom) { { rangeVm.shift(1) } } else vm::nextBillingPeriod,
                deltaText = deltaDescription,
                // В режиме «Даты» нажатие на подпись открывает календарь выбора диапазона
                onDateClick = if (custom) { { showRangePicker = true } } else null,
            )
        }

        // За один день «средняя в день» и «прогноз» равны итогу — карточка ничего не добавляет
        if (sum > 0 && pace != null && spanOf(range) != StatsSpan.DAY) {
            item(key = "pace", contentType = "pace") { SpendingPaceCard(pace = pace, endDate = paceEnd, prevSum = prevSum, span = spanOf(range)) }
        }

        // План трат задаётся на расчётный период; в режимах День / Неделя / Даты объясняем, где его смотреть
        if (plans.isNotEmpty() || totalLimit != null) {
            item(key = "plan", contentType = "plan") {
                if (!custom) {
                    PlanSummaryCard(
                        items = plans,
                        total = totalLimit,
                        period = period,
                        onOpen = onOpenBudget,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                } else {
                    Text(
                        "План трат считается по расчётному периоду: он виден в режиме «Период».",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                    )
                }
            }
        }

        // Долги по рассрочкам и кредитам считаются на сегодня и не зависят от режима
        debtsOverviewItem(debtsVm, onOpenDebts, onOpenDebt)

        if (!custom && cumulative.first.points.isNotEmpty() && cumulative.second.last > 0) {
            item(key = "cumulative", contentType = "cumulative") { CumulativeCard(current = cumulative.first, previous = cumulative.second) }
        }

        // Календарь из одной клетки в режиме «День» бесполезен
        if (sum > 0 && heat != null && spanOf(range) != StatsSpan.DAY) {
            item(key = "heat", contentType = "heat") {
                HeatCalendarCard(month = heat, selected = heatSelected, onDay = { d -> heatSelected = d })
            }
        }

        // «Что изменилось» есть во всех режимах; если сравнивать не с чем — карточка говорит об этом
        if (totals.isNotEmpty()) {
            item(key = "top-changes", contentType = "top-changes") {
                TopChangesCard(
                    changes = if (comparable.isEmpty()) emptyList() else topChanges,
                    compareLabel = compareLabel,
                    emptyText = if (comparable.isEmpty()) "Сравнивать пока не с чем: ${emptyCompareText(spanOf(range))} трат нет."
                    else "По категориям всё так же, как раньше.",
                )
            }
        }

        if (totals.isNotEmpty()) {
            item(key = "ring", contentType = "ring") { CategoryRingCard(slices = ringSlices, sum = sum) }
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

        items(totals, key = { "cat:${it.categoryId}" }, contentType = { "category" }) { t ->
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

                val plan = if (custom) null else categoryPlans[t.categoryId]
                if (plan != null) {
                    Text(
                        text = planLineText(plan.progress),
                        style = TratyTheme.typography.labelSmall,
                        color = if (plan.progress.status == PlanStatus.OK) TratyTheme.colors.onSurfaceVariant
                        else planColor(plan.progress.status),
                        modifier = Modifier.padding(top = TratyTheme.spacing.xxs),
                    )
                }

                if (isExpanded && breakdown != null) {
                    val mode = modes[t.categoryId]?.takeIf { it in breakdown.modes } ?: breakdown.defaultMode
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    CategoryBreakdownView(
                        breakdown = breakdown,
                        labels = labels,
                        mode = mode,
                        onMode = { modes = modes + (t.categoryId to it) },
                        isGroupExpanded = { g -> groupKey(t.categoryId, mode, g) in expandedGroups },
                        onToggleGroup = { g ->
                            val k = groupKey(t.categoryId, mode, g)
                            expandedGroups = if (k in expandedGroups) expandedGroups - k else expandedGroups + k
                        },
                        onOpenExpense = { vm.editing = it },
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
                        TratyButton(onClick = { vm.requestInsight(periodTotals, periodPrev) }) {
                            Text(if (vm.insight == null) "Получить анализ" else "Обновить анализ")
                        }
                    }
                }
            }
        }
    }
}

    heatSelected?.let { day ->
        val zone = ZoneId.systemDefault()
        val dayExpenses = remember(expenses, day) {
            expenses.filter { Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() == day }
                .sortedBy { it.expense.timestamp }
        }
        DayExpensesSheet(
            day = day,
            labels = labels,
            expenses = dayExpenses,
            categoryColors = ringColorMap(remember(totals) { RingSlices.of(totals) }),
            onOpenDay = {
                heatSelected = null
                rangeVm.setRange(day, day)
            },
            onOpenExpense = {
                heatSelected = null
                vm.editing = it
            },
            onDismiss = { heatSelected = null },
        )
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
internal fun LegendDot(color: Color) {
    Canvas(Modifier.padding(end = 4.dp).size(8.dp)) { drawCircle(color) }
}

/**
 * Категориальная палитра долей (фиксированный порядок, свои ступени для тёмной темы), проверена
 * валидатором палитр: различимость при дальтонизме ΔE ≥ 8. Часть цветов бледнее 3:1 к фону,
 * поэтому у каждой доли всегда подпись с названием и %. «Остальное» — серое.
 */
private val RING_COLORS_LIGHT = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4), Color(0xFF6250D6),
)
private val RING_COLORS_DARK = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181), Color(0xFF9085E9),
)
internal val RING_REST = Color(0xFF94A3B8)

@Composable
internal fun ringPalette(): List<Color> = if (TratyTheme.colors.isDark) RING_COLORS_DARK else RING_COLORS_LIGHT

/** Цвет категории = цвет её доли в кольце периода, чтобы одна категория везде была одного цвета. */
@Composable
private fun ringColorMap(slices: List<RingSlice>): Map<Long, Color> {
    val pal = ringPalette()
    return slices.mapIndexedNotNull { i, sl -> sl.categoryId?.let { it to pal[i % pal.size] } }.toMap()
}

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Цвет клетки календаря: один синий оттенок, бледнее — меньше, насыщеннее — больше (последовательная шкала). */
@Composable
private fun heatColor(level: Int): Color =
    if (level == 0) TratyTheme.colors.surfaceContainerHigh
    else ringPalette()[0].copy(alpha = 0.22f + 0.78f * level / HeatCalendar.LEVELS)

/**
 * Тепловой календарь: чем больше потрачено за день, тем насыщеннее клетка (4 уровня от самого дорогого дня).
 * Нажатие на день открывает его траты в нижней шторке.
 */
@Composable
private fun HeatCalendarCard(month: HeatMonth, selected: LocalDate?, onDay: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surfaceContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Календарь трат", style = TratyTheme.typography.titleSmall)
            Text(
                text = "Нажмите на день, чтобы увидеть траты",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.sm))
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
                                val isSel = day.date == selected
                                val isFuture = day.date.isAfter(today)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(TratyTheme.shapes.extraSmall)
                                        .background(heatColor(day.level))
                                        .then(
                                            if (isSel || day.date == today) {
                                                Modifier.border(
                                                    BorderStroke(if (isSel) 2.dp else 1.dp, TratyTheme.colors.onSurface),
                                                    TratyTheme.shapes.extraSmall,
                                                )
                                            } else Modifier
                                        )
                                        .then(if (isFuture) Modifier else Modifier.clickable { onDay(day.date) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.date.dayOfMonth.toString(),
                                        style = TratyTheme.typography.labelSmall,
                                        color = when {
                                            day.level >= 3 -> Color.White
                                            isFuture -> TratyTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
                                            else -> TratyTheme.colors.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Легенда шкалы: «меньше ▢▢▢▢▢ больше» и самый дорогой день
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("меньше", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                (0..HeatCalendar.LEVELS).forEach { lvl ->
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColor(lvl)),
                    )
                }
                Text(
                    "больше",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (month.maxTotal > 0) {
                    Text(
                        "макс. ${formatRub(month.maxTotal)}",
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Где не было трат, когда сравнивать не с чем. */
private fun emptyCompareText(span: StatsSpan) = when (span) {
    StatsSpan.PERIOD -> "в эти же дни прошлого периода"
    StatsSpan.DAY -> "в предыдущий день"
    StatsSpan.WEEK -> "на прошлой неделе"
    StatsSpan.DATES -> "в предыдущие такие же даты"
}

/**
 * «Что изменилось»: три категории с самой большой разницей в рублях.
 * Каждая строка — «было → стало» двумя тонкими полосами одного оттенка (раньше — бледная, сейчас — насыщенная)
 * на общей шкале, справа — разница в ₽ и %. Цвет текста — обычный, направление несёт значок со знаком.
 */
@Composable
private fun TopChangesCard(changes: List<CategoryChange>, compareLabel: String, emptyText: String) {
    val nowColor = TratyTheme.colors.primary
    val wasColor = TratyTheme.colors.primary.copy(alpha = 0.3f)
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
            if (changes.isEmpty()) {
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(emptyText, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurfaceVariant)
                return@Column
            }
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(nowColor)
                Text("сейчас", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
                Spacer(Modifier.width(TratyTheme.spacing.md))
                LegendDot(wasColor)
                Text("раньше", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
            }
            val scale = changes.maxOf { maxOf(it.current, it.previous) }.coerceAtLeast(1)
            changes.forEach { ch ->
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${ch.emoji} ${ch.name}",
                        style = TratyTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    ChangeChip(ch)
                }
                Spacer(Modifier.height(4.dp))
                ComparisonBar(ch.current, scale, nowColor)
                Spacer(Modifier.height(2.dp))
                ComparisonBar(ch.previous, scale, wasColor)
                Text(
                    text = if (ch.previous == 0L) "раньше не было · сейчас ${formatRub(ch.current)}"
                    else if (ch.current == 0L) "было ${formatRub(ch.previous)} · сейчас не было"
                    else "было ${formatRub(ch.previous)} → сейчас ${formatRub(ch.current)}",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Тонкая полоса «было / стало» на общей шкале [scale]; ноль — пустая дорожка. */
@Composable
private fun ComparisonBar(value: Long, scale: Long, color: Color) {
    val frac = (value.toFloat() / scale).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(6.dp)) {
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

/** Разница в ₽ и % на подложке: рост трат — красноватая, снижение — зеленоватая. */
@Composable
private fun ChangeChip(ch: CategoryChange) {
    val up = ch.diff > 0
    val pct = ch.percent?.let { if (it > 0) "+$it%" else "$it%" }
    Box(
        Modifier
            .clip(TratyTheme.shapes.extraSmall)
            .background(if (up) TratyTheme.colors.expenseContainer else TratyTheme.colors.incomeContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = formatRubSigned(ch.diff) + (pct?.let { " · $it" } ?: ""),
            style = TratyTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (up) TratyTheme.colors.expense else TratyTheme.colors.income,
        )
    }
}
