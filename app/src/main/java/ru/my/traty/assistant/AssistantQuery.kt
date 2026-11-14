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
    const val UNKNOWN = "unknown"

    val ALL = listOf(TOTAL, TOP, BREAKDOWN, PLACES, COMPARE, AVERAGE, SUBSCRIPTIONS, INCOME, AFFORD, ADVICE, UNKNOWN)
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
}
