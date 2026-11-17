package ru.my.traty.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.IncomeSources
import ru.my.traty.data.Rule
import java.io.IOException
import java.time.LocalDate

enum class EntryType { EXPENSE, INCOME }

/** Ошибка HTTP от языковой модели (в отличие от сетевой — обрыва, отсутствия сети). */
class LlmHttpException(val code: Int, message: String) : IOException(message)

/** Одна операция из фразы: трата или пополнение. [type] — сырая строка от модели. */
@Serializable
data class ParsedEntry(
    val type: String = "expense",
    val amount: Double = 0.0,
    val category: String = DefaultCategories.OTHER,
    val emoji: String? = null,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** Только для income: источник из [IncomeSources.ALL]. */
    val source: String? = null,
    /** YYYY-MM-DD или null (= день, когда фраза произнесена). */
    val date: String? = null,
    /** Покупка частями: аванс, рассрочка, кредит, доплата. null — обычная трата. */
    val part: PurchaseTerms? = null,
    /** Товары внутри одной покупки: «телефон 40 000, чехол 2 000». null — товары не перечислены. */
    @SerialName("goods") val items: List<ParsedItem>? = null,
) {
    /** null — тип неизвестен (например, «goal_deposit» из будущих этапов). */
    val entryType: EntryType?
        get() = when (type.trim().lowercase()) {
            "expense" -> EntryType.EXPENSE
            "income" -> EntryType.INCOME
            else -> null
        }
}

/**
 * Товар из покупки. [price] — сколько заплачено за позицию целиком (₽), [qty] и [unit] — «2 шт», «1,5 кг».
 * [pack] — фасовка одной штуки текстом: «200 мл», «500 г».
 * Всё, чего нет во фразе, — null. Суммы позиций складывает приложение, а не ИИ.
 */
@Serializable
data class ParsedItem(
    val name: String = "",
    val price: Double? = null,
    val qty: Double? = null,
    val unit: String? = null,
    val pack: String? = null,
)

/**
 * Разобранный ответ модели. [dropped] — сколько записей из ответа отброшено как испорченные
 * (непонятное поле, отрицательная или нереальная сумма): о них нужно сказать человеку, а не потерять молча.
 */
internal data class DecodedEntries(val items: List<ParsedEntry>, val dropped: Int)

/** Больше этой суммы в одной трате (₽) — почти наверняка ошибка модели. */
internal const val MAX_ENTRY_RUBLES = 100_000_000.0

object EntryParsing {
    suspend fun parse(
        client: LlmClient,
        text: String,
        categories: List<CategoryWithSubs>,
        rules: List<Rule>,
        today: LocalDate,
    ): List<ParsedEntry> = parseChecked(client, text, categories, rules, today).items

    internal suspend fun parseChecked(
        client: LlmClient,
        text: String,
        categories: List<CategoryWithSubs>,
        rules: List<Rule>,
        today: LocalDate,
    ): DecodedEntries {
        val raw = client.complete(EntryPrompt.system(categories, today, rules), text, jsonMode = true)
        return decodeChecked(raw)
    }

    internal fun decode(raw: String): List<ParsedEntry> = decodeChecked(raw).items

    /**
     * Модели иногда оборачивают JSON в ```json ... ``` или возвращают голый массив — обрабатываем оба случая.
     * Каждая запись разбирается отдельно: одна испорченная не отбрасывает остальные.
     * Если из ответа не уцелело ни одной записи — ошибка, и фраза уйдёт в разбор без ИИ.
     */
    internal fun decodeChecked(raw: String): DecodedEntries {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arrStart = cleaned.indexOf('[')
        val objStart = cleaned.indexOf('{')
        if (arrStart == -1 && objStart == -1) throw IllegalStateException("ИИ вернул ответ без данных")
        val root: JsonElement = if (arrStart != -1 && (objStart == -1 || arrStart < objStart)) {
            lenientJson.parseToJsonElement(cleaned.substring(arrStart, cleaned.lastIndexOf(']') + 1))
        } else {
            lenientJson.parseToJsonElement(cleaned.substring(objStart, cleaned.lastIndexOf('}') + 1))
        }
        val elements: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> when (val list = root["items"]) {
                is JsonArray -> list
                else -> if ("amount" in root) listOf(root) else emptyList()
            }
            else -> emptyList()
        }
        var dropped = 0
        val decoded = elements.mapNotNull { el ->
            val entry = runCatching { lenientJson.decodeFromJsonElement(ParsedEntry.serializer(), el) }.getOrNull()
                ?.let(::sanitize)
            if (entry == null) dropped++
            entry
        }
        // Покупка в кредит без оплаты сегодня приходит с amount = 0, но с ценой: её не теряем
        val items = decoded.filter { it.amount > 0 || (it.entryType == EntryType.EXPENSE && (it.part?.total ?: 0.0) > 0) }.map {
            if (it.entryType == EntryType.INCOME) it.copy(source = IncomeSources.normalize(it.source)) else it
        }
        if (items.isEmpty() && dropped > 0) throw IllegalStateException("ИИ вернул записи, которые не удалось разобрать")
        return DecodedEntries(items, dropped)
    }

    /** null — запись испорчена: сумма не число, отрицательная или нереальная. Длинные тексты обрезаются. */
    private fun sanitize(e: ParsedEntry): ParsedEntry? {
        if (!e.amount.isFinite() || e.amount < 0 || e.amount > MAX_ENTRY_RUBLES) return null
        val total = e.part?.total
        if (total != null && (!total.isFinite() || total < 0 || total > MAX_ENTRY_RUBLES)) return null
        return e.copy(
            category = e.category.trim().take(MAX_NAME),
            emoji = e.emoji?.take(MAX_EMOJI),
            subcategory = e.subcategory?.trim()?.take(MAX_NAME),
            place = e.place?.trim()?.take(MAX_NAME),
            note = e.note?.trim()?.take(MAX_NOTE),
            source = e.source?.trim()?.take(MAX_NAME),
            date = e.date?.trim()?.take(MAX_DATE),
            items = e.items?.let(::sanitizeItems),
        )
    }

    /** Испорченная цена или количество у товара — не повод терять всю трату: их просто нет. */
    private fun sanitizeItems(items: List<ParsedItem>): List<ParsedItem>? = items
        .filter { it.name.isNotBlank() }
        .take(MAX_ITEMS)
        .map { i ->
            i.copy(
                name = i.name.trim().take(MAX_ITEM_NAME),
                price = i.price?.takeIf { it.isFinite() && it > 0 && it <= MAX_ENTRY_RUBLES },
                qty = i.qty?.takeIf { it.isFinite() && it > 0 && it < MAX_QTY },
                unit = i.unit?.trim()?.take(MAX_UNIT)?.ifEmpty { null },
                pack = i.pack?.trim()?.take(MAX_ITEM_NAME)?.ifEmpty { null },
            )
        }
        .ifEmpty { null }

    private const val MAX_NAME = 60
    private const val MAX_EMOJI = 8
    private const val MAX_NOTE = 500
    private const val MAX_DATE = 20
    private const val MAX_ITEMS = 50
    private const val MAX_ITEM_NAME = 80
    private const val MAX_UNIT = 10
    private const val MAX_QTY = 100_000.0
}

/**
 * Запасной разбор без ИИ: одна операция; тип — по словам «зарплата / аванс / пришло / вернули…».
 * «аванс за холодильник», «доплата» — трата ([PurchasePayment]).
 * После него всегда нужно подтверждение.
 */
object OfflineEntryParser {
    // «пришлось заплатить штраф» — не пришедшие деньги; «премиум» — не премия
    private val incomeWords = Regex("""зарплат|аванс|дежур|пришл(?!ось)|преми[яюи](?![\p{L}])|вернул|возврат|подработ|кэшбэк|кешбэк""")

    fun parse(text: String, categories: List<CategoryWithSubs>, today: LocalDate = LocalDate.now()): List<ParsedEntry> {
        if (incomeWords.containsMatchIn(text.lowercase()) && !PurchasePayment.isPurchasePayment(text)) {
            val day = SpokenDateParser.find(text, today)?.date?.toString()
            return OfflineIncomeParser.parse(text).map {
                ParsedEntry(type = "income", amount = it.amount, source = it.source, note = it.note, date = it.date ?: day)
            }
        }
        return OfflineParser.parse(text, categories, today).map {
            ParsedEntry(
                type = "expense", amount = it.amount, category = it.category, emoji = it.emoji,
                subcategory = it.subcategory, place = it.place, note = it.note, date = it.date,
                part = PurchaseTermsParser.find(text),
            )
        }
    }
}
