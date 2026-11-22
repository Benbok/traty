package ru.my.traty.assistant

import kotlinx.serialization.Serializable
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

@Serializable
data class ProductReportOptions(
    val metric: String? = null,
    val compare: Boolean? = null,
    val orderByChange: Boolean? = null,
    val direction: String? = null,
    val repeated: Boolean? = null,
    val items: List<String>? = null,
    val followUp: Boolean? = null,
    val shownOnly: Boolean? = null,
    val allProducts: Boolean? = null,
)

internal object ProductReportRequest {
    private val products = Regex("""(?<!\p{L})(?:товар|продукт)\p{L}*""")
    private val groceries = Regex("""(?<!\p{L})продукт\p{L}*""")
    private val list = Regex("""(?:какие|список|покажи|отчет).*(?:купил|покупа|товар|продукт)""")
    private val comparison = Regex("""сравн|чем\s+(?:в|за)\s+прошл|вырос|снизил|стал[аи]?\s+(?:тратить|покупать)""")
    private val repeat = Regex("""повторно|больше одного раза|несколько раз""")
    private val selected = Regex("""из\s+(?:этих|них)""")
    private val allProducts = Regex("""(?:все|всем|всех)\s+товар""")
    private val numbers = listOf("один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять")
    private val number = """\d{1,2}|""" + numbers.joinToString("|")
    private val count = Regex("""(?<![\p{L}\d])(""" + number +
        """)\s+(?:товар|продукт)|(?:топ[\s-]*|первые\s+|^покажи\s+)(""" + number + """)(?![\p{L}\d])""")

    fun supports(intent: String?) = intent == Intents.PRODUCT_REPORT || intent == Intents.PRODUCT_RANK

    private fun limit(text: String): Int? = count.find(text)?.groupValues?.drop(1)
        ?.firstOrNull { it.isNotEmpty() }?.let { it.toIntOrNull() ?: numbers.indexOf(it) + 1 }

    private fun metric(text: String): String? = when {
        "в штуках" in text || "по количеству штук" in text -> "pieces"
        "по весу" in text || "по массе" in text || "в килограммах" in text -> "weight"
        "по объему" in text || "в литрах" in text -> "volume"
        Regex("""чаще|реже|по\s+частоте""").containsMatchIn(text) -> "frequency"
        Regex("""потрат|расход|по\s+сумме|ушло""").containsMatchIn(text) -> "spending"
        else -> null
    }

    private fun direction(text: String): String? = when {
        Regex("""сниз|меньше|реже""").containsMatchIn(text) -> "decrease"
        Regex("""вырос|больше|чаще""").containsMatchIn(text) -> "increase"
        else -> null
    }

    fun guess(text: String, categories: List<CategoryWithSubs>): AssistantQuery? {
        if (!products.containsMatchIn(text)) return null
        val previousRank = ProductRanking.guess(text)
        val compare = comparison.containsMatchIn(text)
        val repeated = repeat.containsMatchIn(text)
        val limit = limit(text)
        if (!compare && !repeated && limit == null && previousRank != null) return null
        if (!compare && !repeated && limit == null && !list.containsMatchIn(text)) return null
        return AssistantQuery(
            intent = Intents.PRODUCT_REPORT,
            categories = if (allProducts.containsMatchIn(text)) emptyList() else
                QuestionRules.categoriesIn(text, categories).ifEmpty {
                    listOf("Продукты").filter { groceries.containsMatchIn(text) }
                },
            limit = limit,
            productReport = ProductReportOptions(
                metric = previousRank?.productRankBy ?: metric(text),
                compare = true.takeIf { compare },
                direction = direction(text).takeIf { compare },
                repeated = true.takeIf { repeated },
            ),
        )
    }

    fun isContinuation(question: String, previous: AssistantQuery?, today: LocalDate, startDay: Int): Boolean {
        if (!supports(previous?.intent)) return false
        val text = fold(question)
        return selected.containsMatchIn(text) || allProducts.containsMatchIn(text) ||
            (text.startsWith("а ") && (text.startsWith("а только ") || metric(text) != null ||
                QuestionDates.find(question, today, startDay).isNotEmpty())) ||
            (limit(text) != null && !products.containsMatchIn(text))
    }

    fun resolve(
        question: String, model: AssistantQuery?, guessed: AssistantQuery?, previous: AssistantQuery?,
        today: LocalDate, startDay: Int,
    ): AssistantQuery? {
        if (model?.intent in IntentRegistry.COMMANDS) return null
        val text = fold(question)
        val ai = model?.takeIf { supports(it.intent) }
        val local = guessed?.takeIf { supports(it.intent) }
        val follow = previous?.takeIf { supports(it.intent) &&
            (ai?.productReport?.followUp == true || isContinuation(question, previous, today, startDay)) }
        if (follow == null && ai?.intent != Intents.PRODUCT_REPORT && local?.intent != Intents.PRODUCT_REPORT) return null
        val incoming = ai?.productReport ?: ProductReportOptions()
        val parsed = local?.productReport ?: ProductReportOptions()
        val prior = follow?.productReport
        val compare = parsed.compare ?: incoming.compare ?: true.takeIf { comparison.containsMatchIn(text) } ?: prior?.compare
            ?: (ai?.compareFrom != null || ai?.compareTo != null)
        val clear = incoming.allProducts == true || allProducts.containsMatchIn(text)
        val shown = !clear && (incoming.shownOnly == true || selected.containsMatchIn(text) ||
            (prior?.shownOnly == true && incoming.items == null))
        val store = text.substringAfter("только в ", "").trim(' ', '?', '.', '«', '»').ifEmpty { null }
        val scope = ProductRanking.scope(question, ai?.copy(intent = Intents.PRODUCT_RANK), local, previous, today, startDay)
        val measure = (parsed.metric ?: local?.productRankBy ?: metric(text) ?: incoming.metric
            ?: ai?.productRankBy ?: prior?.metric ?: follow?.productRankBy ?: "spending")
            .takeIf { it in setOf("spending", "frequency", "pieces", "weight", "volume") } ?: "spending"
        val resolved = AssistantQuery(
            intent = Intents.PRODUCT_REPORT,
            categories = if (clear) emptyList() else local?.categories.orEmpty()
                .ifEmpty { ai?.categories.orEmpty() }.ifEmpty { follow?.categories.orEmpty() },
            place = store ?: ai?.place ?: scope.place ?: follow?.place,
            from = ai?.from ?: follow?.from?.takeUnless { compare && it == QuestionDates.ALL_TIME.toString() },
            to = ai?.to ?: follow?.to,
            compareFrom = ai?.compareFrom ?: follow?.compareFrom,
            compareTo = ai?.compareTo ?: follow?.compareTo,
            limit = limit(text) ?: ai?.limit ?: local?.limit ?: follow?.limit
                ?: AssistantEngine.DEFAULT_TOP.takeIf { ai?.intent == Intents.PRODUCT_RANK || local?.intent == Intents.PRODUCT_RANK },
            productReport = ProductReportOptions(
                metric = measure, compare = compare,
                orderByChange = incoming.orderByChange ?: prior?.orderByChange ?: compare,
                direction = (incoming.direction ?: parsed.direction ?: direction(text) ?: prior?.direction).takeIf { compare },
                repeated = incoming.repeated ?: parsed.repeated ?: prior?.repeated,
                items = if (clear) null else incoming.items ?: prior?.items,
                shownOnly = shown,
            ),
            shownProductKeys = if (shown) follow?.shownProductKeys.orEmpty() else emptyList(),
        )
        return dates(question, resolved, today, startDay, inherited = follow != null)
    }

    private fun dates(question: String, query: AssistantQuery, today: LocalDate, startDay: Int, inherited: Boolean): AssistantQuery {
        val compare = query.productReport?.compare == true
        val spans = QuestionDates.find(question, today, startDay, joinMonths = !compare)
        if (spans.isEmpty()) {
            if (!compare && !inherited && query.productReport?.metric == "frequency") {
                return query.copy(from = QuestionDates.ALL_TIME.toString(), to = null)
            }
            return QuestionDates.fixFuture(query, today)
        }
        if (!compare) return query.copy(from = spans.minOf { it.from }.toString(), to = spans.maxOf { it.to }.toString())
        val ordered = spans.take(2).sortedByDescending { it.from }
        val period = BillingPeriod.containing(today, startDay)
        val previousOnly = ordered.size == 1 && Regex("""(?:чем\s+|сравн\p{L}*\s+с\s+)(?:в\s+|за\s+)?(?:прошл|предыдущ)""")
            .containsMatchIn(fold(question))
        val current = if (previousOnly) DateSpan(period.startDate, today) else ordered.first()
        val earlier = if (previousOnly) ordered.first() else ordered.getOrNull(1)
        val previous = period.previous(startDay)
        val shorthand = Regex("""(?:этом|текущем|этот|текущий)\s+(?:месяц|период)""")
        val end = if (earlier != null && current == DateSpan(period.startDate, today) &&
            earlier == DateSpan(previous.startDate, previous.endDate) && (previousOnly || shorthand.containsMatchIn(fold(question)))
        ) minOf(earlier.to, earlier.from.plusDays(current.to.toEpochDay() - current.from.toEpochDay())) else earlier?.to
        return query.copy(from = current.from.toString(), to = current.to.toString(),
            compareFrom = earlier?.from?.toString(), compareTo = end?.toString())
    }
}
