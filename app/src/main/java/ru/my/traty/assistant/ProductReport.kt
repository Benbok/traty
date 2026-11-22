package ru.my.traty.assistant

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.ItemNames
import ru.my.traty.data.items.ItemSale
import ru.my.traty.data.items.ItemTotals
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.formatRub

data class ProductReportRow(
    val key: String,
    val name: String,
    val detail: String,
    val currentExpenseIds: List<Long>,
    val previousExpenseIds: List<Long>,
)

data class ProductReportResult(val title: String, val rows: List<ProductReportRow>, val footer: String) {
    val shownKeys: List<String> get() = rows.map { it.key }
}

internal object ProductReport {
    private val petFood = Regex("""(?<!\p{L})корм\p{L}*""")
    private data class Basket(
        val totals: Map<String, ProductTotal>, val ids: Map<String, List<Long>>,
        val detailed: Int, val purchases: Int,
    )

    fun run(q: AssistantQuery, ctx: AssistantContext, spending: List<ExpenseWithCategory>): AssistantData {
        val options = q.productReport ?: ProductReportOptions(metric = q.productRankBy ?: "frequency")
        val metric = options.metric ?: "spending"
        val period = BillingPeriod.containing(ctx.today, ctx.startDay)
        val (requested, to) = range(q.from, q.to, period.startDate, ctx.today)
        val allTime = requested == QuestionDates.ALL_TIME
        val from = if (allTime) spending.minOfOrNull { day(it.expense.timestamp, ctx.zone) } ?: requested else requested
        val current = DateSpan(from, to)
        val previous = if (options.compare == true) previousSpan(q, current, period, ctx) else null
        val filter = Filter.of(q, spending, ctx.categories, ctx.expenseItems)
        filter.unresolvedAnswer(spending, ctx)?.let {
            return AssistantData("", direct = "Категория отчёта не найдена. Уточните название категории.")
        }
        if (options.shownOnly == true && q.shownProductKeys.isEmpty()) {
            return AssistantData("", direct = "Уточните, какие товары включить в отчёт.")
        }
        val groceries = q.categories.any { fold(it) == "продукты" }
        val knownGroceries = if (groceries) spending.asSequence()
            .filter { fold(it.categoryName) == "продукты" }
            .flatMap { ctx.expenseItems[it.expense.id].orEmpty().asSequence() }
            .map { ItemNames.familyKey(it.name) }.toSet() else emptySet()

        fun basket(dates: DateSpan): Basket {
            val receipts = spending.filter { day(it.expense.timestamp, ctx.zone) in dates.from..dates.to }
            val sales = mutableListOf<ItemSale>()
            var purchases = 0
            var detailed = 0
            val store = Filter(emptySet(), emptyList(), filter.place)
            val named = options.items.orEmpty()
            val keys = q.shownProductKeys.toSet()
            for (receipt in receipts) {
                val expense = receipt.expense
                val items = ctx.expenseItems[expense.id].orEmpty()
                val matches = filter.matches(receipt, items)
                val eligible = if (matches) items else if (groceries && store.matches(receipt)) {
                    items.filter { ItemNames.familyKey(it.name) in knownGroceries }
                } else emptyList()
                if (!matches && eligible.isEmpty()) continue
                purchases++
                if (eligible.isNotEmpty()) detailed++
                eligible.filterNot { groceries && petFood.containsMatchIn(fold(it.name)) }.forEach { item ->
                    if (named.isNotEmpty() && named.none { item.mentionsItem(it) }) return@forEach
                    if (options.shownOnly == true && ItemNames.familyKey(item.name) !in keys) return@forEach
                    sales += ItemSale(item.name, item.priceKopecks, item.quantityMilli, item.unit, expense.id,
                        expense.timestamp, expense.place, expense.categoryId, item.packMilli, item.packUnit)
                }
            }
            return Basket(ItemTotals.byProduct(sales, keyOf = ItemNames::familyKey).associateBy { it.key },
                sales.groupBy { ItemNames.familyKey(it.name) }.mapValues { (_, items) -> items.map { it.expenseId }.distinct() },
                detailed, purchases)
        }

        val now = basket(current)
        val old = previous?.let(::basket)
        val ordered = (now.totals.keys + old?.totals.orEmpty().keys).map { key ->
            Comparison(key, now.totals[key], old?.totals?.get(key))
        }.filter { row ->
            val diff = row.change(metric)
            val directionMatches = when (options.direction) {
                "increase" -> diff != null && diff > 0
                "decrease" -> diff != null && diff < 0
                else -> true
            }
            directionMatches && (options.repeated != true || maxOf(row.current?.purchases ?: 0, row.previous?.purchases ?: 0) > 1) &&
                listOfNotNull(row.current, row.previous).any { value(it, metric) != null || metric == "spending" }
        }.sortedWith(compareByDescending<Comparison> { row ->
            if (old != null && options.orderByChange != false) {
                row.change(metric)?.let { if (options.direction == "decrease") -it else it }
            } else row.current?.let { value(it, metric) }
        }.thenBy { it.name }.thenBy { it.key })

        val limit = q.limit ?: AssistantEngine.DEFAULT_TOP.takeIf { q.intent == Intents.PRODUCT_RANK }
        val rows = ordered.take(limit?.coerceIn(1, AssistantEngine.MAX_TOP) ?: ordered.size).map { row ->
            ProductReportRow(row.key, row.name, row.describe(metric, old != null),
                now.ids[row.key].orEmpty(), old?.ids?.get(row.key).orEmpty())
        }
        val rangeLabel = if (allTime) "за всё время (${span(from, to)})" else "за ${span(from, to)}"
        val scope = q.categories.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " · ").orEmpty()
        val location = filter.place?.let { " в магазине «$it»" }.orEmpty()
        val title = "Товары ${metricLabel(metric)} $rangeLabel$location$scope" +
            (previous?.let { "\nСравнение с ${span(it.from, it.to)}" } ?: "")
        val footer = buildList {
            if (rows.isEmpty()) add("Подходящих позиций с нужными данными нет.")
            if (old == null && metric == "frequency" && rows.isNotEmpty() && ordered.all { it.current?.purchases == 1 }) {
                add("За этот период каждый найденный товар встречается в одной покупке.")
            }
            if (rows.size < ordered.size) add("Показано ${rows.size} из ${ordered.size} товаров.")
            if (old == null) {
                add("Состав чека известен для ${now.detailed} из ${now.purchases} покупок.")
            } else {
                add("Покупки с детализацией: текущий период — ${now.detailed} из ${now.purchases}, предыдущий — ${old.detailed} из ${old.purchases}.")
                if (rows.any { it.previousExpenseIds.isEmpty() || it.currentExpenseIds.isEmpty() }) {
                    add("Изменение рассчитано по известным позициям чеков.")
                }
            }
        }.joinToString("\n")
        val result = ProductReportResult(title, rows, footer)
        val text = (listOf(title) + rows.map { "• ${it.name} — ${it.detail}" } + listOf(footer)).joinToString("\n")
        return AssistantData("", direct = text,
            expenseIds = rows.flatMap { it.currentExpenseIds + it.previousExpenseIds }.distinct(), productReport = result)
    }

    private fun previousSpan(q: AssistantQuery, current: DateSpan, period: BillingPeriod, ctx: AssistantContext): DateSpan {
        if (q.compareFrom != null || q.compareTo != null) {
            val (from, to) = range(q.compareFrom, q.compareTo, current.from.minusDays(1), current.from.minusDays(1))
            return DateSpan(from, to)
        }
        val length = current.to.toEpochDay() - current.from.toEpochDay()
        if (current.from == period.startDate) {
            val prior = period.previous(ctx.startDay)
            return DateSpan(prior.startDate, minOf(prior.endDate, prior.startDate.plusDays(length)))
        }
        if (current.from.dayOfMonth == 1 && current.to == current.from.plusMonths(1).minusDays(1)) {
            return DateSpan(current.from.minusMonths(1), current.from.minusDays(1))
        }
        return DateSpan(current.from.minusDays(length + 1), current.from.minusDays(1))
    }

    private data class Comparison(val key: String, val current: ProductTotal?, val previous: ProductTotal?) {
        val name: String get() = ItemNames.familyName(requireNotNull(current ?: previous).name)

        fun change(metric: String): Long? {
            if (metric == "spending" && (current?.unpriced ?: 0) + (previous?.unpriced ?: 0) > 0) return null
            val a = if (current == null) 0L else value(current, metric) ?: return null
            val b = if (previous == null) 0L else value(previous, metric) ?: return null
            return a - b
        }

        fun describe(metric: String, compared: Boolean): String {
            if (compared) {
                val before = previous?.let { primary(it, metric) } ?: "нет в чеках"
                val after = current?.let { primary(it, metric) } ?: "нет в чеках"
                val difference = change(metric)?.let { " · ${if (it > 0) "+" else ""}${display(it, metric)}" }.orEmpty()
                return "$before → $after$difference"
            }
            val item = requireNotNull(current)
            return when (metric) {
                "spending" -> listOfNotNull(primary(item, metric), purchases(item.purchases.toLong()), item.amounts.known())
                "frequency" -> listOfNotNull(purchases(item.purchases.toLong()), item.amounts.known(), primary(item, "spending"))
                else -> listOf(primary(item, metric), purchases(item.purchases.toLong()), primary(item, "spending"))
            }.joinToString(" · ")
        }
    }

    private fun primary(item: ProductTotal, metric: String): String {
        val amount = value(item, metric) ?: return if (metric == "spending") "цена не указана" else "количество неизвестно"
        return display(amount, metric) + if (metric == "spending" && item.unpriced > 0) " (сумма неполная)" else ""
    }

    private fun value(item: ProductTotal, metric: String): Long? = when (metric) {
        "frequency" -> item.purchases.toLong()
        "pieces" -> item.amounts.piecesMilli.takeIf { it > 0 }
        "weight" -> item.amounts.gramsMilli.takeIf { it > 0 }
        "volume" -> item.amounts.mlMilli.takeIf { it > 0 }
        else -> item.totalKopecks.takeIf { it != 0L || item.unpriced == 0 }
    }

    private fun display(amount: Long, metric: String): String {
        val sign = if (amount < 0) "−" else ""
        val absolute = kotlin.math.abs(amount)
        return when (metric) {
            "frequency" -> purchases(amount)
            "pieces" -> "$sign${ItemMeasure.number(absolute)} шт"
            "weight" -> "$sign${ItemMeasure.weight(absolute)}"
            "volume" -> "$sign${ItemMeasure.volume(absolute)}"
            else -> formatRub(amount)
        }
    }

    private fun purchases(count: Long): String {
        val n = kotlin.math.abs(count)
        val word = when {
            n % 100 in 11..14 -> "покупок"
            n % 10 == 1L -> "покупка"
            n % 10 in 2..4 -> "покупки"
            else -> "покупок"
        }
        return "$count $word"
    }

    private fun metricLabel(metric: String): String = when (metric) {
        "frequency" -> "по числу покупок"
        "pieces" -> "по количеству штук"
        "weight" -> "по весу"
        "volume" -> "по объёму"
        else -> "по сумме"
    }
}
