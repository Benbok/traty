package ru.my.traty.assistant

import ru.my.traty.data.CategoryWithSubs
import java.time.LocalDate

/**
 * Запасной разбор вопроса правилами, без ИИ: когда ИИ не ответил (нет сети), вернул не JSON
 * или решил, что вопрос не про деньги, хотя в нём явно «сколько я потратил».
 */
object QuestionRules {
    private class Rule(pattern: String, val intent: String) {
        val regex = Regex(pattern)
    }

    // Порядок важен: первое совпадение решает. Узкие формулировки — раньше общих.
    private val RULES = listOf(
        Rule("""подписк|регулярн\S*\s+платеж""", Intents.SUBSCRIPTIONS),
        Rule("""могу\s+ли|хватит\s+ли|позволить|по\s+карману|успею\s+ли""", Intents.AFFORD),
        Rule("""сравни|по\s+сравнению|чем\s+в\s+прошл|больше\s+чем|меньше\s+чем""", Intents.COMPARE),
        Rule("""по\s+месяцам|по\s+неделям|по\s+дням|(день|дн\S*)\s+недели|динамик|как\s+менял|как\s+изменял""", Intents.TREND),
        Rule("""последн\S*\s+раз|как\s+часто|сколько\s+раз|покажи\s+(мои\s+)?(траты|покупки)|список\s+(трат|покупок)|что\s+я\s+(купил|покупал|брал)""", Intents.LIST),
        Rule("""в\s+среднем|средн\S*\s+(трат|чек|покупк|в\s+день)""", Intents.AVERAGE),
        Rule("""по\s+плану|перерасход|лимит|план\S*\s+трат|бюджет""", Intents.PLAN),
        Rule("""сэконом|эконом|совет|тратить\s+меньше""", Intents.ADVICE),
        Rule("""баланс|на\s+сч[её]т[еу]|сколько\s+(у\s+меня\s+)?денег""", Intents.BALANCE),
        Rule("""доход|заработ|поступлен|зарплат|в\s+плюсе|в\s+минусе""", Intents.INCOME),
        Rule("""(^|\s)топ|сам\S*\s+(крупн|дорог)|крупн\S*\s+покупк""", Intents.TOP),
        Rule("""(^|\s)где(\s|$)|в\s+как\S*\s+(магазин|мест)""", Intents.PLACES),
        Rule("""на\s+что\s+(ушл|уход|я\s+трач|трач)|куда\s+(ушл|уход|дел)|разбивк|по\s+категориям""", Intents.BREAKDOWN),
        Rule("""потрат|ушло|трачу|трат[аыи]?(\s|$)|расход|сколько\s+(я\s+)?(отдал|заплатил|вышло)""", Intents.TOTAL),
    )

    /** Запрос по ключевым словам; null — в вопросе нет ничего про деньги. */
    fun guess(question: String, categories: List<CategoryWithSubs>): AssistantQuery? {
        val text = fold(question)
        val intent = RULES.firstOrNull { it.regex.containsMatchIn(text) }?.intent ?: return null
        return AssistantQuery(
            intent = intent,
            categories = categoriesIn(text, categories),
            amount = if (intent == Intents.AFFORD) AssistantQueries.amountIn(text) else null,
            limit = if (intent == Intents.TOP) Regex("""топ[\s-]*(\d{1,2})""").find(text)?.groupValues?.get(1)?.toIntOrNull() else null,
            groupBy = if (intent == Intents.TREND) groupIn(text) else null,
        )
    }

    private fun groupIn(text: String): String = when {
        Regex("""(день|дн\S*)\s+недели""").containsMatchIn(text) -> GroupBy.WEEKDAY
        "по неделям" in text -> GroupBy.WEEK
        "по дням" in text -> GroupBy.DAY
        else -> GroupBy.MONTH
    }

    /** Категории и подкатегории, названные в вопросе: «кафе» → «Кафе и рестораны», «такси» → «Такси». */
    internal fun categoriesIn(text: String, categories: List<CategoryWithSubs>): List<String> {
        val words = stems(text)
        fun named(name: String) = stems(name).any { s -> s.length >= 4 && words.any { w -> w.startsWith(s) || (w.length >= 4 && s.startsWith(w)) } }
        val subs = categories.flatMap { c -> c.subs.map { it.name } }.filter(::named)
        val cats = categories.map { it.category.name }.filter(::named)
        return (subs + cats).distinct()
    }

    /**
     * Итоговый запрос: ответ ИИ, а если его нет или ИИ не узнал вопрос — правила.
     * Поверх — явные команды ([AssistantQueries.correct]) и даты из вопроса ([QuestionDates]).
     */
    fun resolve(question: String, fromAi: AssistantQuery?, guessed: AssistantQuery?, today: LocalDate, startDay: Int): AssistantQuery {
        val base = fromAi?.takeIf { it.intent != Intents.UNKNOWN } ?: guessed ?: fromAi ?: AssistantQuery(intent = Intents.UNKNOWN)
        return QuestionDates.apply(question, AssistantQueries.correct(question, base), today, startDay)
    }
}

/** Ответ без второго обращения к ИИ: если он не ответил, показываем то, что посчитало приложение. */
object AssistantFallback {
    const val NO_AI = "ИИ сейчас не ответил, поэтому вот что посчитало приложение:"

    /** [reason] — понятный текст ошибки ИИ («неверный ключ», «нет сети»), если он есть. */
    fun noAiGuessed(reason: String?): String =
        "ИИ сейчас недоступен${reason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}, " +
            "поэтому я понял вопрос по ключевым словам. Вот что посчитало приложение:"

    fun text(data: AssistantData, guessed: Boolean = false, reason: String? = null): String {
        val lines = data.facts.lines()
            .filterNot { it.startsWith("Сегодня ") && it.endsWith("Суммы в рублях.") }
            .map { it.replace(" — сравнение не приводи", "") }
            .filter { it.isNotBlank() }
        return (if (guessed) noAiGuessed(reason) else NO_AI) + "\n" + lines.joinToString("\n")
    }
}
