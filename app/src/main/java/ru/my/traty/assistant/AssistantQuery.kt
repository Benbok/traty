package ru.my.traty.assistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
)

object Intents {
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
    const val UNKNOWN = "unknown"

    val ALL = listOf(TOTAL, TOP, BREAKDOWN, PLACES, COMPARE, AVERAGE, SUBSCRIPTIONS, INCOME, AFFORD, ADVICE, PLAN, BALANCE, SUGGEST_PLAN, SET_PLAN, UNKNOWN)
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

    private val setPlanRe = Regex("""(постав|установ|задай|задать|сделай|измени|поменяй|обнови)\S*\s+(мне\s+)?(лимит|план|бюджет)""")
    private val suggestPlanRe = Regex("""(предлож|состав|подбер|рассчита|посчита|прикин)\S*.*(план|лимит|бюджет)|^\s*(план|бюджет)\s+(трат|расходов)?\s*(на\s+(месяц|период))?\s*[.!?]*\s*$|какие\s+лимиты""")
    private val amountRe = Regex("""(\d[\d\s\u00A0]*(?:[.,]\d+)?)\s*(тыс\S*|т\.?|к)?(?![\p{L}\d])""")

    /**
     * Команды про план модель иногда принимает за вопрос («поставь лимит на кафе 2500» → afford/plan).
     * Явные формулировки распознаём сами: так кнопка «Добавить в план» появляется всегда.
     */
    fun correct(question: String, q: AssistantQuery): AssistantQuery {
        val text = question.lowercase().replace('ё', 'е')
        return when {
            setPlanRe.containsMatchIn(text) -> {
                val amount = q.amount?.takeIf { it > 0 } ?: amountIn(text)
                if (amount == null) q else q.copy(intent = Intents.SET_PLAN, amount = amount)
            }
            q.intent != Intents.SET_PLAN && suggestPlanRe.containsMatchIn(text) -> q.copy(intent = Intents.SUGGEST_PLAN)
            else -> q
        }
    }

    /** Последнее число в тексте, в рублях: «3 000» → 3000, «3 тысячи» / «3к» → 3000. */
    internal fun amountIn(text: String): Double? {
        val m = amountRe.findAll(text).lastOrNull() ?: return null
        val n = m.groupValues[1].filter { !it.isWhitespace() }.replace(',', '.').trimEnd('.').toDoubleOrNull() ?: return null
        val v = if (m.groupValues[2].isNotEmpty()) n * 1000 else n
        return v.takeIf { it > 0 }
    }
}
