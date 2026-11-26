package ru.my.traty.assistant

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.labels.ExpenseLabel
import ru.my.traty.data.labels.ExpenseLabelHit
import java.time.LocalDate

/** Метки приложения образуют отдельный фильтр поверх категории, места и периода. */
internal object AssistantLabelQuestions {
    private val aliases = mapOf(
        ExpenseLabel.IMPULSIVE to Regex("""(?<![\p{L}])импульсивн\p{L}*(?![\p{L}])"""),
        ExpenseLabel.LARGE to Regex("""(?<![\p{L}])необычно\s+крупн\p{L}*(?![\p{L}])"""),
        ExpenseLabel.FREQUENT to Regex("""(?<![\p{L}])част(?:ая|ые|ую|ой|ых|ыми|ым)(?![\p{L}])(?=\s+(?:трат|покупк|расход))|метк\p{L}*\s*[«"„]?част\p{L}*"""),
        ExpenseLabel.SUBSCRIPTION to Regex("""(?<![\p{L}])похож\p{L}*\s+на\s+подписк\p{L}*(?![\p{L}])"""),
        ExpenseLabel.NEW_PLACE to Regex("""(?<![\p{L}])нов(?:ое|ом|ого|ому|ые|ых|ыми|ым)\s+мест\p{L}*(?![\p{L}])"""),
    )

    private val readIntents = setOf(
        Intents.TOTAL, Intents.LIST, Intents.TOP, Intents.TREND, Intents.COMPARE,
        Intents.AVERAGE, Intents.PLACES, Intents.BREAKDOWN,
    )
    private val labelPrefix = Regex("""(?<![\p{L}])(?:с\s+)?метк\p{L}*(?![\p{L}])""")
    private val listWords = Regex("""(?<![\p{L}])(?:покажи\p{L}*|список|перечисли\p{L}*|какие|когда)(?![\p{L}])""")
    private val allPurchases = Regex("""(?<![\p{L}])все\s+(?:\p{L}+\s+){0,3}(?:покупк|трат|расход)\p{L}*(?![\p{L}])""")
    private val dateFollowUp = Regex("""^а\s+(?:в|за|с|по|вчера|сегодня)(?![\p{L}])""")

    private fun withoutLabels(text: String) = aliases.values.fold(text) { result, alias ->
        alias.replace(result, " ")
    }.replace(labelPrefix, " ")

    fun guess(
        text: String,
        categories: List<CategoryWithSubs>,
        parseUnlabelled: (String) -> AssistantQuery?,
    ): AssistantQuery? {
        val labels = aliases.filterValues { it.containsMatchIn(text) }.keys.toList()
        if (labels.isEmpty()) return null
        val plain = withoutLabels(text)
        val parsed = parseUnlabelled(plain)
        val intent = parsed?.intent?.takeIf { it in readIntents && it != Intents.TOTAL }
            ?: if (listWords.containsMatchIn(plain)) Intents.LIST else Intents.TOTAL
        return AssistantQuery(
            intent = intent,
            categories = QuestionRules.categoriesIn(plain, categories),
            from = QuestionDates.ALL_TIME.toString().takeIf { allPurchases.containsMatchIn(text) },
            limit = parsed?.limit,
            groupBy = parsed?.groupBy,
            labels = labels,
        )
    }

    fun resolve(
        question: String,
        local: AssistantQuery?,
        model: AssistantQuery?,
        previous: AssistantQuery?,
        categories: List<CategoryWithSubs>,
        today: LocalDate,
        startDay: Int,
    ): AssistantQuery? {
        val text = fold(question)
        val plain = withoutLabels(text)
        val query = when {
            local != null && local.labels.isNotEmpty() -> {
                val command = ExpenseCommands.detect(text, local, previous, categories)
                val relevantModel = model?.takeIf { it.intent in readIntents }
                command ?: local.copy(
                    place = relevantModel?.place?.takeIf { place -> fold(place).takeIf { it.isNotEmpty() }?.let { it in plain } == true },
                    from = local.from ?: relevantModel?.from,
                    to = relevantModel?.to.takeIf { local.from == null },
                    compareFrom = relevantModel?.compareFrom,
                    compareTo = relevantModel?.compareTo,
                )
            }
            model != null && model.labels.isNotEmpty() && model.intent in readIntents -> model.copy(
                categories = model.categories.filter { withoutLabels(fold(it)) == fold(it) },
            )
            previous != null && previous.labels.isNotEmpty() && previous.intent in readIntents &&
                dateFollowUp.containsMatchIn(text) && QuestionDates.find(question, today, startDay).isNotEmpty() ->
                previous.copy(from = null, to = null, compareFrom = null, compareTo = null)
            else -> return null
        }
        return QuestionDates.apply(question, query, today, startDay)
    }

    fun attachMatches(query: AssistantQuery, hits: Map<Long, List<ExpenseLabelHit>>): AssistantQuery {
        val labels = query.labels.toSet()
        return query.copy(labelExpenseIds = hits.asSequence()
            .filter { (_, expenseHits) -> expenseHits.any { it.label in labels } }
            .map { it.key }
            .toSet())
    }
}
