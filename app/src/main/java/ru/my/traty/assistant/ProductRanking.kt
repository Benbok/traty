package ru.my.traty.assistant

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemNames
import ru.my.traty.data.items.ItemSale
import ru.my.traty.data.items.ItemTotals
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.ui.formatRub
import java.time.LocalDate

internal object ProductRanking {
    private const val FREQUENCY = "frequency"
    private const val PIECES = "pieces"
    private const val SPENDING = "spending"
    private val product = Regex("""(?:^|[^\p{L}])(?:товар|продукт)\p{L}*""")
    private val groceries = Regex("""(?:^|[^\p{L}])(?:продукт\p{L}*|ед[ауые]|продовольственн\p{L}*)(?!\p{L})""")
    private val petFood = Regex("""(?:^|[^\p{L}])корм\p{L}*(?!\p{L})""")
    private val genericStore = Regex("""(?:магазин\p{L}*|супермаркет\p{L}*)(?:\s+продукт\p{L}*)?""")
    private val frequency = Regex("""популярн|частот|чаще\s+всего|чаще\s+других|покупа\p{L}*\s+чаще""")
    private val spending = Regex("""потрат|тратил|ушло|по\s+сумме|по\s+расходам|обошл""")
    private val pieces = Regex("""в\s+штуках|по\s+количеству|больше\s+всего\s+штук""")
    private val superlative = Regex("""больше\s+всего|больше\s+всех|(?:^|\s)топ""")
    private val productTop = Regex("""топ(?:[\s-]+\d+)?\s+(?:товар|продукт)""")
    private val bought = Regex("""(?:^|[^\p{L}])(?:купил|покупал|беру|брал|покупаю)\p{L}*""")
    private val openQuestion = Regex("""^(?:а\s+)?(?:какие|каких|какого|что|чего|назови|покажи|топ)(?=\s|$)""")
    private val limit = Regex("""(?:топ[\s-]*|первые\s+)(\d{1,2})(?!\d)""")
    private val reference = Regex("""за\s+(?:(?:вот\s+)?этот\s+(?:весь\s+)?период|весь\s+этот\s+период)""")
    private val storeClause = Regex("""(?:^|\s)((?:в|во|у)\s+(?!(?:меня|нас|тебя|вас)(?=\s|$))(?:«[^»]+»|[\p{L}\p{N}][\p{L}\p{N} .-]*?))(?=\s+(?:за|в|во|у|вчера|сегодня|мы|я|купил|брали|покупал|чаще|больше|по)(?=\s|$)|[?!,]|$)""")

    fun guess(text: String): AssistantQuery? {
        val hasProduct = product.containsMatchIn(text)
        val asksWhat = openQuestion.containsMatchIn(text)
        val metric = when {
            pieces.containsMatchIn(text) && (asksWhat || productTop.containsMatchIn(text)) -> PIECES
            hasProduct && spending.containsMatchIn(text) && superlative.containsMatchIn(text) -> SPENDING
            frequency.containsMatchIn(text) && (hasProduct || (asksWhat && bought.containsMatchIn(text))) -> FREQUENCY
            productTop.containsMatchIn(text) -> FREQUENCY
            else -> return null
        }
        return AssistantQuery(intent = Intents.PRODUCT_RANK, productRankBy = metric,
            categories = if (hasProduct && groceries.containsMatchIn(text)) listOf("Продукты") else emptyList(),
            limit = limit.find(text)?.groupValues?.get(1)?.toIntOrNull())
    }

    fun scope(question: String, model: AssistantQuery?, local: AssistantQuery?, previous: AssistantQuery?,
        today: LocalDate, startDay: Int): AssistantQuery {
        val text = fold(question)
        val rank = model?.takeIf { it.intent == Intents.PRODUCT_RANK }
        val dateReference = reference.containsMatchIn(text) && QuestionDates.find(question, today, startDay).isEmpty()
        val localPlace = storeClause.findAll(text).firstNotNullOfOrNull { match ->
            val clause = match.groupValues[1].trim()
            if (clause in setOf("в принципе", "в целом", "в общем", "в основном", "в штуках") ||
                QuestionDates.find(clause, today, startDay).isNotEmpty()) null
            else clause.substringAfter(' ').trim(' ', '«', '»', '.', '!').takeUnless(genericStore::matches)
        }
        val hasHistory = dateReference && (previous?.from != null || previous?.to != null)
        return QuestionDates.apply(question, AssistantQuery(
            intent = Intents.PRODUCT_RANK,
            categories = rank?.categories.orEmpty().ifEmpty { local?.categories.orEmpty() },
            productRankBy = local?.productRankBy ?: rank?.productRankBy,
            limit = local?.limit ?: rank?.limit,
            place = rank?.place?.trim()?.takeUnless { genericStore.matches(fold(it)) } ?: localPlace,
            from = if (hasHistory) previous?.from else model?.from.takeUnless { dateReference },
            to = if (hasHistory) previous?.to else model?.to.takeUnless { dateReference },
        ), today, startDay)
    }

    fun answer(query: AssistantQuery, rows: List<ExpenseWithCategory>, expenseItems: Map<Long, List<ExpenseItem>>,
        span: String): AssistantData {
        val groceryOnly = query.categories.any { fold(it) == "продукты" }
        val sales = rows.flatMap { row ->
            val expense = row.expense
            expenseItems[expense.id].orEmpty().filterNot { groceryOnly && petFood.containsMatchIn(fold(it.name)) }.map { item ->
                ItemSale(item.name, item.priceKopecks, item.quantityMilli, item.unit, expense.id,
                    expense.timestamp, expense.place, expense.categoryId, item.packMilli, item.packUnit)
            }
        }
        val totals = ItemTotals.byProduct(sales)
        val metric = when (query.productRankBy) {
            PIECES -> "по количеству штук"
            SPENDING -> "по сумме"
            else -> "по числу покупок"
        }
        val comparator = when (query.productRankBy) {
            PIECES -> compareByDescending<ProductTotal> { it.amounts.piecesMilli }
            SPENDING -> compareByDescending<ProductTotal> { it.totalKopecks }
            else -> compareByDescending<ProductTotal> { it.purchases }
        }.thenByDescending { it.purchases }.thenBy { it.name }
        val byFrequency = query.productRankBy != PIECES && query.productRankBy != SPENDING
        val hasRepeatedPurchases = byFrequency && totals.any { it.purchases > 1 }
        val ranked = totals.asSequence()
            .filter { !hasRepeatedPurchases || it.purchases > 1 }
            .filter { query.productRankBy != PIECES || it.amounts.piecesMilli > 0 }
            .filter { query.productRankBy != SPENDING || it.totalKopecks != 0L || it.unpriced == 0 }
            .sortedWith(comparator)
            .toList()
        val top = ranked.take((query.limit ?: AssistantEngine.DEFAULT_TOP).coerceIn(1, AssistantEngine.MAX_TOP))
        val location = query.place?.let { " в магазине «$it»" }.orEmpty()
        if (top.isEmpty()) {
            val note = if (totals.isEmpty()) "В чеках нет записанных позиций." else "Для рейтинга $metric нет данных."
            return AssistantData(facts = "", direct = "За $span$location. $note")
        }

        val withoutItems = rows.count { expenseItems[it.expense.id].isNullOrEmpty() }
        val noRepeatedPurchases = byFrequency && !hasRepeatedPurchases
        val keys = top.map { it.key }.toSet()
        val receiptIds = sales.filter { ItemNames.key(it.name) in keys }.map { it.expenseId }.distinct()
        val report = buildString {
            if (noRepeatedPurchases) {
                appendLine("За $span$location каждый найденный товар встречается в одной покупке.")
            } else {
                appendLine("Товары $metric за $span$location")
            }
            top.forEach { entry ->
                val purchases = "${entry.purchases} ${purchaseWord(entry.purchases)}"
                val quantity = entry.amounts.text().takeIf { it.isNotBlank() }
                val total = formatRub(entry.totalKopecks).takeIf { entry.totalKopecks != 0L || entry.unpriced == 0 }
                val details = when (query.productRankBy) {
                    PIECES -> listOfNotNull(quantity, purchases, total)
                    SPENDING -> listOfNotNull(total, purchases, quantity)
                    else -> listOfNotNull(purchases, quantity, total)
                }
                val pricing = if (entry.unpriced > 0) " · без цены: ${entry.unpriced} поз." else ""
                appendLine("• ${entry.name} — ${details.joinToString(" · ")}$pricing")
            }
            if (top.size < ranked.size) appendLine("Показано ${top.size} из ${ranked.size} товаров.")
            if (withoutItems > 0) append("Состав чека известен для ${rows.size - withoutItems} из ${rows.size} покупок.")
        }
        return AssistantData(facts = "", direct = report.trimEnd(), expenseIds = receiptIds)
    }

    private fun purchaseWord(count: Int): String = when {
        count % 100 in 11..14 -> "покупок"
        count % 10 == 1 -> "покупка"
        count % 10 in 2..4 -> "покупки"
        else -> "покупок"
    }
}
