package ru.my.traty.ui.stats

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import ru.my.traty.ui.home.ExpenseRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatDateWithWeekday
import java.time.LocalDate

/**
 * Траты одного дня в нижней шторке: дата, итог и число покупок, полоса долей по категориям,
 * затем сами траты в том же виде, что на главной. Кнопка открывает статистику этого дня.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayExpensesSheet(
    day: LocalDate,
    expenses: List<ExpenseWithCategory>,
    categoryColors: Map<Long, Color>,
    labels: Map<Long, List<ru.my.traty.data.labels.ExpenseLabelHit>> = emptyMap(),
    onOpenDay: () -> Unit,
    onOpenExpense: (ExpenseWithCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = TratyTheme.colors.surface,
    ) {
        val total = expenses.sumOf { it.expense.amountKopecks }
        Column(Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.lg)) {
            Text(
                text = formatDateWithWeekday(day).replaceFirstChar { it.uppercase() },
                style = TratyTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                TratyAmountText(amountKopecks = total, size = AmountSize.LARGE, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Text(
                    text = if (expenses.isEmpty()) "трат нет" else purchases(expenses.size),
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (expenses.isNotEmpty()) {
                // Доли категорий дня одной тонкой полосой (сегменты через 2dp зазор), подписи — ниже
                val byCat = expenses.groupBy { it.expense.categoryId }
                    .map { (id, v) -> Triple(id, "${v.first().categoryEmoji} ${v.first().categoryName}", v.sumOf { it.expense.amountKopecks }) }
                    .sortedByDescending { it.third }
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    byCat.forEachIndexed { i, (id, _, v) ->
                        Box(
                            Modifier
                                .weight(v.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .padding(end = if (i < byCat.lastIndex) 2.dp else 0.dp)
                                .background(categoryColors[id] ?: RING_REST),
                        )
                    }
                }
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                byCat.forEach { (id, name, v) ->
                    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(categoryColors[id] ?: RING_REST)
                        Text(
                            name,
                            style = TratyTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatRub(v), style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(TratyTheme.spacing.sm))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            expenses.forEach { e -> ExpenseRow(row = e, onClick = { onOpenExpense(e) }, labels = labels[e.expense.id].orEmpty()) }
        }
        TratyButton(
            onClick = onOpenDay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        ) { Text("Статистика за этот день") }
        Spacer(Modifier.height(TratyTheme.spacing.lg))
    }
}
