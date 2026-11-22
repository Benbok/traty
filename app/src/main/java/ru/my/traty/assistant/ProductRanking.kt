package ru.my.traty.assistant

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
            from = when {
                hasHistory -> previous?.from
                dateReference -> null
                (local?.productRankBy ?: rank?.productRankBy ?: FREQUENCY) == FREQUENCY -> QuestionDates.ALL_TIME.toString()
                model?.from != null || model?.to != null -> model?.from
                else -> null
            },
            to = if (hasHistory) previous?.to else model?.to.takeUnless {
                dateReference || (local?.productRankBy ?: rank?.productRankBy ?: FREQUENCY) == FREQUENCY
            },
        ), today, startDay)
    }
}
