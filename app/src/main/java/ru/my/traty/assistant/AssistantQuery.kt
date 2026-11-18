package ru.my.traty.assistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import ru.my.traty.data.CategoryWithSubs

/** Что спросил пользователь — в виде запроса к данным. ИИ переводит в него вопрос, считает приложение. */
@Serializable
data class AssistantQuery(
    val intent: String = Intents.UNKNOWN,
    /** Названия категорий или подкатегорий из списка приложения. */
    val categories: List<String> = emptyList(),
    /** Часть названия места или заметки: «Пятёрочка», «Яндекс». */
    val place: String? = null,
    /** Даты в формате ГГГГ-ММ-ДД, включительно. */
    val from: String? = null,
    val to: String? = null,
    /** Второй отрезок — для сравнения. */
    val compareFrom: String? = null,
    val compareTo: String? = null,
    /** Сумма в рублях — для «могу ли я позволить». */
    val amount: Double? = null,
    /** Сколько позиций показать в топе. */
    val limit: Int? = null,
    /** amount — не новая сумма, а прибавка (отрицательная — уменьшение): «увеличь лимит на 1000». */
    val relative: Boolean = false,
    /** Для trend: month, week, weekday или day ([GroupBy]). */
    val groupBy: String? = null,
    /** «эти», «их», «все»: действие относится ко всему, что было в прошлом ответе. */
    val all: Boolean = false,
    /** Для move_expense: категория или подкатегория, в которую переносят траты. */
    val target: String? = null,
    /** Вместе с [relative]: amount — не рубли, а проценты от действующего плана («подними лимит на 10 процентов»). */
    val percent: Boolean = false,
    /**
     * Траты из прошлого ответа, к которым относятся «удали их», «перенеси эти»: их проставляет приложение, а не ИИ.
     * В JSON не попадает: ни в запрос к ИИ, ни в историю вопросов.
     */
    @Transient val ids: List<Long> = emptyList(),
    val housing: ru.my.traty.assistant.apartments.ApartmentQuestion? = null,
    /** Название товара в явном вопросе о количестве купленного. Заполняется приложением. */
    val purchasedItem: String? = null,
)

object Intents {
    const val METER_READINGS = "meter_readings"
    const val RESOURCE_CONSUMPTION = "resource_consumption"
    const val RESOURCE_COMPARE = "resource_compare"
    const val RESOURCE_TREND = "resource_trend"
    const val TOTAL = "total"
    const val TOP = "top"
    const val BREAKDOWN = "breakdown"
    const val PLACES = "places"
    const val COMPARE = "compare"
    const val AVERAGE = "average"
    const val SUBSCRIPTIONS = "subscriptions"
    const val INCOME = "income"
    const val AFFORD = "afford"
    const val ADVICE = "advice"
    const val PLAN = "plan"
    const val BALANCE = "balance"
    const val SUGGEST_PLAN = "suggest_plan"
    const val SET_PLAN = "set_plan"
    const val REMOVE_PLAN = "remove_plan"
    const val CANCEL_SUB = "cancel_subscription"
    const val PAUSE_SUB = "pause_subscription"
    const val RESUME_SUB = "resume_subscription"
    const val EDIT_SUB = "edit_subscription"
    const val TREND = "trend"
    const val LIST = "list"
    const val DEBTS = "debts"
    const val DELETE_DEBT = "delete_debt"
    const val CLOSE_DEBT = "close_debt"
    const val CANCEL_DEBT = "cancel_debt"
    const val DELETE_EXPENSE = "delete_expense"
    const val MOVE_EXPENSE = "move_expense"
    const val UNKNOWN = "unknown"

    val ALL: List<String> = IntentRegistry.ALL.map { it.key }
}

object AssistantQueries {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Модели иногда оборачивают JSON в ```json … ``` или пишут текст вокруг — берём первый объект. null — не JSON. */
    fun decode(raw: String): AssistantQuery? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        val q = runCatching { json.decodeFromString(AssistantQuery.serializer(), raw.substring(start, end + 1)) }.getOrNull()
            ?: return null
        return if (q.intent in Intents.ALL) q else q.copy(intent = Intents.UNKNOWN)
    }

    fun encode(q: AssistantQuery): String = json.encodeToString(AssistantQuery.serializer(), q)

    private val setPlanRe = Regex("""(постав|установ|задай|задать|сделай|измени|поменяй|обнови|увелич|подним|повыс|уменьш|сниз|убав)\S*\s+(мне\s+)?(лимит|план|бюджет)""")
    private val upRe = Regex("""(увелич|подним|повыс|добавь\s+к)\S*""")
    private val downRe = Regex("""(уменьш|сниз|убав|урежь)\S*""")
    private val removePlanRe = Regex("""(убер|убр|удал|сним|снят|отмен|отключ|сбрось)\S*\s+(?:мне\s+|мой\s+|мои\s+|весь\s+|все\s+|всё\s+)*(лимит|план|бюджет)""")
    private val suggestPlanRe = Regex("""(предлож|состав|подбер|рассчита|посчита|прикин)\S*.*(план|лимит|бюджет)|^\s*(план|бюджет)\s+(трат|расходов)?\s*(на\s+(месяц|период))?\s*[.!?]*\s*$|какие\s+лимиты""")
    private val percentRe = Regex("""(\d+(?:[.,]\d+)?)\s*(?:%|процент\S*)""")
    private val amountRe = Regex("""(\d[\d\s\u00A0]*(?:[.,]\d+)?)\s*(тыс\S*|т\.?|к)?(?![\p{L}\d])""")
    private val subWordRe = Regex("""подписк\S*|регулярн\S*\s+платеж\S*|платеж\S*""")
    private val cancelSubRe = Regex("""(отмен|удал|убер|убр|отключ|отпис|прекрат|выкин)\S*.*(подписк|платеж)|отпис\S*\s+(меня\s+)?от""")
    private val pauseSubRe = Regex("""на\s+паузу|приостанов|заморо[зж]|пауз""")
    private val editSubRe = Regex("""(измени|поменяй|обнови|исправь|перенеси|скорректируй|подправь)\S*.*(подписк|платеж)""")
    private val debtRe = Regex("""(?<![\p{L}])(долг\S*|должен\S*|должна|кредит\S*|рассрочк\S*|частями|долями|переплат\S*)""")
    private val spentRe = Regex("""потрат|ушло|трачу|расход""")
    private val resumeSubRe = Regex("""(возобнов|верни|включи|восстанов|продолж)\S*.*(подписк|платеж)|сними\s+с\s+паузы""")

    /**
     * Команды про план и подписки модель иногда принимает за вопрос («поставь лимит на кафе 2500» → afford/plan).
     * Явные формулировки распознаём сами: так кнопка подтверждения появляется всегда.
     */
    fun correct(
        question: String,
        q: AssistantQuery,
        previous: AssistantQuery? = null,
        categories: List<CategoryWithSubs> = emptyList(),
    ): AssistantQuery {
        val text = question.lowercase().replace('ё', 'е')
        DebtCommands.detect(text, q, previous)?.let { return it }
        ExpenseCommands.detect(text, q, previous, categories)?.let { return it }
        val sub = subWordRe.containsMatchIn(text)
        // «сколько я должен», «когда платёж по рассрочке» модель иногда принимает за total или subscriptions
        // «сколько я потратил на кредит» и вопрос про категорию «Долги и кредиты» остаются тратами
        val aboutSpending = q.categories.isNotEmpty() || spentRe.containsMatchIn(text)
        if (debtRe.containsMatchIn(text) && !aboutSpending && q.intent in setOf(Intents.TOTAL, Intents.SUBSCRIPTIONS, Intents.LIST, Intents.UNKNOWN, Intents.BALANCE)) {
            return q.copy(intent = Intents.DEBTS)
        }
        return when {
            sub && resumeSubRe.containsMatchIn(text) -> q.copy(intent = Intents.RESUME_SUB, place = q.place ?: subNameIn(text))
            sub && pauseSubRe.containsMatchIn(text) -> q.copy(intent = Intents.PAUSE_SUB, place = q.place ?: subNameIn(text))
            sub && editSubRe.containsMatchIn(text) && q.intent != Intents.EDIT_SUB ->
                q.copy(intent = Intents.EDIT_SUB, place = q.place ?: subNameIn(text), amount = q.amount ?: amountIn(text))
            sub && cancelSubRe.containsMatchIn(text) -> q.copy(intent = Intents.CANCEL_SUB, place = q.place ?: subNameIn(text))
            removePlanRe.containsMatchIn(text) -> q.copy(intent = Intents.REMOVE_PLAN)
            setPlanRe.containsMatchIn(text) -> {
                // «подними лимит на 10 процентов»: 10 — это проценты от плана, а не десять рублей
                val pct = percentRe.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
                val amount = pct ?: q.amount?.takeIf { it != 0.0 }?.let { kotlin.math.abs(it) } ?: amountIn(text)
                val absolute = Regex("""(?:\s|^)до\s+\d""").containsMatchIn(text) || Regex("""(постав|установ|задай|задать)""").containsMatchIn(text)
                val relative = !absolute && (upRe.containsMatchIn(text) || downRe.containsMatchIn(text))
                when {
                    amount == null -> q
                    pct != null -> if (relative) {
                        q.copy(intent = Intents.SET_PLAN, amount = if (downRe.containsMatchIn(text)) -pct else pct, relative = true, percent = true)
                    } else q
                    relative -> q.copy(intent = Intents.SET_PLAN, amount = if (downRe.containsMatchIn(text)) -amount else amount, relative = true)
                    else -> q.copy(intent = Intents.SET_PLAN, amount = amount, relative = false)
                }
            }
            q.intent != Intents.SET_PLAN && suggestPlanRe.containsMatchIn(text) -> q.copy(intent = Intents.SUGGEST_PLAN)
            else -> q
        }
    }

    /** Название подписки из фразы «отмени подписку Яндекс Плюс»: то, что идёт после слова «подписка». */
    internal fun subNameIn(text: String): String? {
        val m = subWordRe.find(text) ?: return null
        val rest = text.substring(m.range.last + 1)
            .replace(Regex("""\d[\d\s.,]*\S*"""), " ")
            .split(Regex("""[\s,.!?]+""")).filter { it.isNotEmpty() }
            .filterNot { it in STOP_WORDS }
        return rest.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private val STOP_WORDS = setOf("на", "в", "по", "у", "мою", "мне", "эту", "пожалуйста", "пауза", "паузу", "до", "с", "за", "руб", "рублей", "р", "месяц", "теперь", "сумму", "дату", "списание", "и")

    /** Последнее число в тексте, в рублях: «3 000» → 3000, «3 тысячи» / «3к» → 3000. */
    internal fun amountIn(text: String): Double? {
        val m = amountRe.findAll(text).lastOrNull() ?: return null
        val n = m.groupValues[1].filter { !it.isWhitespace() }.replace(',', '.').trimEnd('.').toDoubleOrNull() ?: return null
        val v = if (m.groupValues[2].isNotEmpty()) n * 1000 else n
        return v.takeIf { it > 0 }
    }
}
