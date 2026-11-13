package ru.my.traty.ui.stats

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    var showRangePicker by remember { mutableStateOf(false) }

    // Режим «Даты» (range != null) не влияет на период ленты трат: он живёт только на этом экране
    val custom = range != null
    val totals = if (custom) customTotals else periodTotals
    val prev = if (custom) customPrev else periodPrev
    val prevMap = remember(prev) { prev.associate { it.categoryId to it.total } }
    val sum = totals.sumOf { it.total }
    val prevSum = prev.sumOf { it.total }

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

        items(totals, key = { it.categoryId }) { t ->
            val frac = if (sum > 0) t.total.toFloat() / sum else 0f
            val p = prevMap[t.categoryId]

            Column(
                Modifier
                    .fillMaxWidth()
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

private fun deltaText(cur: Long, prev: Long): String {
    if (prev == 0L) return "—"
    val pct = ((cur - prev) * 100.0 / prev).roundToInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
}
