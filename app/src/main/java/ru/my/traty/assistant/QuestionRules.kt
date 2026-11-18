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
        // «сколько литров молока я купил», «сколько коктейлей взял» — количество товара, считается вместе с суммой
        Rule("""сколько\s.*(купил|покупал|взял|брал)""", Intents.TOTAL),
        Rule("""в\s+среднем|средн\S*\s+(трат|чек|покупк|в\s+день)""", Intents.AVERAGE),
        Rule("""по\s+плану|перерасход|лимит|план\S*\s+трат|бюджет""", Intents.PLAN),
        Rule("""сэконом|эконом|совет|тратить\s+меньше""", Intents.ADVICE),
        Rule("""баланс|на\s+сч[её]т[еу]|сколько\s+(у\s+меня\s+)?денег""", Intents.BALANCE),
        Rule("""доход|заработ|поступлен|зарплат|в\s+плюсе|в\s+минусе""", Intents.INCOME),
        Rule("""(^|\s)топ|сам\S*\s+(крупн|дорог)|крупн\S*\s+покупк""", Intents.TOP),
        Rule("""(^|\s)где(\s|$)|в\s+как\S*\s+(магазин|мест)""", Intents.PLACES),
        Rule("""на\s+что\s+(ушл|уход|я\s+трач|трач)|куда\s+(ушл|уход|дел)|разбивк|по\s+категориям""", Intents.BREAKDOWN),
        Rule("""потрат|ушло|трачу|трат[аыи]?(\s|$)|расход|сколько\s+(я\s+)?(отдал|заплатил|вышло)""", Intents.TOTAL),
        // «сколько я должен», «когда платёж по рассрочке»; «сколько потратил на кредит» остаётся тратами выше
        Rule("""(^|[^\p{L}])(долг|должен|должна|кредит|рассрочк|частями|долями|переплат)""", Intents.DEBTS),
    )

    /** Запрос по ключевым словам; null — в вопросе нет ничего про деньги. */
    fun guess(question: String, categories: List<CategoryWithSubs>): AssistantQuery? {
        val text = fold(question)
        ProductRanking.guess(text)?.let { return it }
        ItemFlavor.allTimeSubject(text)?.let { return AssistantQuery(intent = Intents.TOTAL, categories = listOf(it)) }
        val item = PurchasedItemQuestion.itemIn(text)
        val intent = RULES.firstOrNull { it.regex.containsMatchIn(text) }?.intent
            ?: if (item != null) Intents.TOTAL else return null
        return AssistantQuery(
            intent = intent,
            purchasedItem = item.takeIf { intent == Intents.TOTAL },
            categories = broadPetCategory(text, intent, categories)?.let(::listOf)
                ?: item?.takeIf { intent == Intents.TOTAL }?.let(::listOf)
                ?: categoriesIn(text, categories).ifEmpty { subjectIn(text) },
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

    private val subjectRe = Regex("""(?:потрат\S*|трач\S*|ушл\S*|уход\S*|отдал\S*|заплатил\S*|вышл\S*)\s+(?:я\s+)?на\s+(\p{L}.*)""")
    private val notSubject = Regex("""^(?:эт|прошл|прошед|последн|текущ|недел|месяц|год|квартал|сегодн|вчера|себя|что|чт|вс[её]|январ|феврал|март|апрел|ма[йяе]|июн|июл|август|сентябр|октябр|ноябр|декабр)""")

    /**
     * Товар или слово после «потратил на», если категорий в вопросе нет: «сколько я потратил на шоколадки» → «шоколадки»,
     * «сколько литров молока я купил» → «молока».
     * Приложение поищет его в списках товаров, местах и заметках ([Filter.of]).
     */
    internal fun subjectIn(text: String): List<String> {
        val word = subjectRe.find(text)?.groupValues?.get(1)?.let(PurchasedItemQuestion::titleBeforeScope)
            ?: PurchasedItemQuestion.itemIn(text) ?: return emptyList()
        return if (notSubject.containsMatchIn(word)) emptyList() else listOf(word)
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
    fun resolve(
        question: String,
        fromAi: AssistantQuery?,
        guessed: AssistantQuery?,
        today: LocalDate,
        startDay: Int,
        previous: AssistantQuery? = null,
        categories: List<CategoryWithSubs> = emptyList(),
    ): AssistantQuery {
        if (guessed?.intent == Intents.PRODUCT_RANK || fromAi?.intent == Intents.PRODUCT_RANK) {
            return ProductRanking.scope(question, fromAi, guessed, previous, today, startDay)
        }
        val base = fromAi?.takeIf { it.intent != Intents.UNKNOWN } ?: guessed ?: fromAi ?: AssistantQuery(intent = Intents.UNKNOWN)
        val qualifiedSubject = ItemFlavor.allTimeSubject(question)
        val item = guessed?.purchasedItem
        val query = when {
            item != null && (base.intent == Intents.TOTAL || base.intent == Intents.LIST) -> base.copy(
                intent = Intents.TOTAL,
                categories = emptyList(),
                purchasedItem = item,
                place = PurchasedItemQuestion.placeIn(question, base.place, today, startDay),
            )
            base.intent == Intents.TOTAL && qualifiedSubject != null &&
                base.place == null && base.categories.size <= 1 -> base.copy(categories = listOf(qualifiedSubject), purchasedItem = null)
            base.intent == Intents.TOTAL && base.categories.isEmpty() && base.place == null && guessed?.intent == Intents.TOTAL ->
                base.copy(categories = guessed.categories, purchasedItem = null)
            else -> base.copy(purchasedItem = null)
        }
        val corrected = AssistantQueries.correct(question, query, previous, categories)
        val broadPet = broadPetCategory(fold(question), corrected.intent, categories)
        val scoped = if (broadPet == null) corrected else corrected.copy(
            categories = listOf(broadPet),
            place = corrected.place?.takeIf { p -> stems(p).let { it.isNotEmpty() && it.all { word -> word in fold(question) } } },
        )
        return QuestionDates.apply(question, scoped, today, startDay)
    }

    // Общий вопрос о питомце не должен случайно сужаться ИИ до одной услуги.
    // Именованные животные и конкретные покупки остаются поиском по словам.
    private val petScopeIntents = setOf(
        Intents.TOTAL, Intents.TOP, Intents.BREAKDOWN, Intents.PLACES,
        Intents.COMPARE, Intents.AVERAGE, Intents.TREND, Intents.LIST,
    )
    private val broadPetRe = Regex(
        """(?:^|[^\p{L}])на\s+(?:(?:мою|моего|моих|свою|своего|своих)\s+)?(?:кошку|кошек|кота|котов|котенка|собаку|собак|щенка|питомца|питомцев)(?=\s*(?:$|[?!.,]|за\s|в\s|с\s|по\s|этот\s|прошлый\s))"""
    )

    private fun broadPetCategory(text: String, intent: String, categories: List<CategoryWithSubs>): String? {
        if (intent !in petScopeIntents || !broadPetRe.containsMatchIn(text)) return null
        val pets = categories.firstOrNull { fold(it.category.name) == "питомцы" } ?: return null
        // «На кошку, только груминг» — явная подкатегория важнее общего названия.
        if (categoriesIn(text, categories).any { fold(it) != fold(pets.category.name) }) return null
        return pets.category.name
    }
}

/** Ответ без второго обращения к ИИ: если он не ответил, показываем то, что посчитало приложение. */
object AssistantFallback {
    const val NO_AI = "ИИ сейчас не ответил, поэтому вот что посчитало приложение:"

    /** Режим «Без ИИ»: вопрос понят по ключевым словам, и это не сбой. */
    const val NO_AI_MODE = "Сейчас выбран разбор без ИИ, поэтому я понял вопрос по ключевым словам. Вот что посчитало приложение:"

    /** Что умеет вкладка без ИИ, когда вопрос не удалось понять по ключевым словам. */
    const val NO_AI_HINT = "Без ИИ я понимаю вопросы по ключевым словам, например: «сколько я потратил на кафе в этом месяце», " +
        "«на что ушли деньги», «топ трат», «где я больше всего трачу», «мои подписки», «сколько я должен», «какой баланс». " +
        "Чтобы спрашивать своими словами, подключите DeepSeek или YandexGPT в Настройках → «Разбор фраз и ИИ»."

    const val UNCHECKED = "В ответе ИИ были числа, которых нет в расчётах приложения, поэтому показываю сами расчёты:"

    /** [reason] — понятный текст ошибки ИИ («неверный ключ», «нет сети»), если он есть. */
    fun noAiGuessed(reason: String?): String =
        "ИИ сейчас недоступен${reason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}, " +
            "поэтому я понял вопрос по ключевым словам. Вот что посчитало приложение:"

    /** Строки посчитанных фактов без служебной шапки. */
    fun bodyLines(data: AssistantData): List<String> = data.facts.lines()
        .filterNot { it.startsWith("Сегодня ") && it.endsWith("Суммы в рублях.") }
        .map { it.replace(" — сравнение не приводи", "") }
        .filter { it.isNotBlank() }

    fun text(data: AssistantData, guessed: Boolean = false, reason: String? = null, noAiMode: Boolean = false): String {
        val head = when {
            noAiMode -> NO_AI_MODE
            guessed -> noAiGuessed(reason)
            else -> NO_AI
        }
        return head + "\n" + bodyLines(data).joinToString("\n")
    }

    fun unchecked(data: AssistantData): String = UNCHECKED + "\n" + bodyLines(data).joinToString("\n")
}
