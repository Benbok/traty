package ru.my.traty.ui.charts.model

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeSources
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class CashFlowKind(val title: String) { INCOME("Доходы"), EXPENSE("Расходы") }

sealed interface CashFlowCategoryKey {
    data class Income(val source: String) : CashFlowCategoryKey
    data class Expense(val id: Long) : CashFlowCategoryKey
}

data class CashFlowCategory(val key: CashFlowCategoryKey, val name: String, val kind: CashFlowKind)

data class CashFlowSeries(val label: String, val amounts: List<Long>)

enum class CashFlowGranularity { DAY, WEEK, MONTH }

data class CashFlowOperation(val title: String, val timestamp: Long, val amount: Long, val seriesIndex: Int)

data class CashFlowGraph(
    val intervals: List<ClosedRange<LocalDate>> = emptyList(),
    val series: List<CashFlowSeries> = emptyList(),
    val granularity: CashFlowGranularity = CashFlowGranularity.DAY,
    val operationsByDay: Map<LocalDate, List<CashFlowOperation>> = emptyMap(),
) {
    val dates: List<LocalDate> get() = intervals.map { it.endInclusive }

    internal fun daysWithin(interval: ClosedRange<LocalDate>): CashFlowGraph {
        val days = generateSequence(interval.start) { it.plusDays(1) }
            .takeWhile { it <= interval.endInclusive }.toList()
        return copy(
            intervals = days.map { it..it },
            series = series.mapIndexed { index, line ->
                line.copy(amounts = days.map { date ->
                    operationsByDay[date].orEmpty().sumOf { operation ->
                        if (operation.seriesIndex == index) operation.amount else 0L
                    }
                })
            },
            granularity = CashFlowGranularity.DAY,
        )
    }
}

internal data class CashFlowSelection(
    val categories: List<CashFlowCategory>,
    val selected: Set<CashFlowCategoryKey>,
    val graph: CashFlowGraph,
)

private data class CashFlowEntry(
    val category: CashFlowCategory,
    val title: String,
    val timestamp: Long,
    val amount: Long,
)

internal const val MAX_COMPARED_CATEGORIES = 2

internal fun buildCashFlowSelection(
    intervals: List<ClosedRange<LocalDate>>,
    granularity: CashFlowGranularity,
    expenses: List<ExpenseWithCategory>,
    incomes: List<Income>,
    kind: CashFlowKind,
    requested: List<CashFlowCategory>,
    zone: ZoneId,
): CashFlowSelection {
    val entries = when (kind) {
        CashFlowKind.INCOME -> incomes.map { income ->
            val source = income.source.trim().ifEmpty { IncomeSources.DEFAULT }
            val category = CashFlowCategory(CashFlowCategoryKey.Income(source.lowercase(Locale.ROOT)), source, kind)
            CashFlowEntry(category, income.note?.takeIf { it.isNotBlank() } ?: source, income.timestamp, income.amountKopecks)
        }
        CashFlowKind.EXPENSE -> expenses.map { row ->
            val expense = row.expense
            val category = CashFlowCategory(CashFlowCategoryKey.Expense(expense.categoryId), row.categoryName, kind)
            val title = listOfNotNull(
                row.subcategoryName ?: row.categoryName,
                expense.place?.takeIf { it.isNotBlank() } ?: expense.note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            CashFlowEntry(category, title, expense.timestamp, expense.amountKopecks)
        }
    }
    val byCategory = entries.groupBy { it.category.key }
    val available = requested.associateBy { it.key } + byCategory.mapValues { (_, rows) -> rows.first().category }
    val categories = available.values.sortedBy { it.name.lowercase(Locale.ROOT) }
    val selected = requested.map { it.key }.toSet()
    if ((entries.isEmpty() && selected.isEmpty()) || intervals.isEmpty()) {
        return CashFlowSelection(categories, selected, CashFlowGraph(granularity = granularity))
    }

    val groups = if (selected.isEmpty()) listOf(kind.title to entries) else {
        requested.map { category ->
            available.getValue(category.key).name to byCategory[category.key].orEmpty()
        }
    }
    val operations = mutableListOf<CashFlowOperation>()
    fun date(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val series = groups.mapIndexed { index, (name, rows) ->
        val sums = rows.groupingBy { date(it.timestamp) }.fold(0L) { total, row -> total + row.amount }
        rows.forEach { operations += CashFlowOperation(it.title, it.timestamp, it.amount, index) }
        CashFlowSeries(name, intervals.map { interval ->
            generateSequence(interval.start) { it.plusDays(1) }
                .takeWhile { it <= interval.endInclusive }
                .sumOf { sums[it] ?: 0L }
        })
    }
    val byDay = operations.sortedByDescending { it.timestamp }.groupBy { date(it.timestamp) }
    return CashFlowSelection(categories, selected, CashFlowGraph(intervals, series, granularity, byDay))
}
