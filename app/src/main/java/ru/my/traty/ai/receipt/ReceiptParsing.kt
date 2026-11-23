package ru.my.traty.ai.receipt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import ru.my.traty.data.items.Pack
import ru.my.traty.data.items.PackSize
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Строка чека после разбора ИИ. Цена — за позицию целиком (все штуки, со скидкой), в копейках.
 * [pack] — фасовка одной штуки («200 мл»), если ИИ её выделил; иначе её поищут в названии.
 */
data class ReceiptLine(
    val name: String,
    val priceKopecks: Long?,
    val quantityMilli: Long? = null,
    val unit: String? = null,
    val pack: Pack? = null,
    val unitPriceKopecks: Long? = null,
    val discountKopecks: Long? = null,
)

/** Чек после разбора ИИ: деньги уже в копейках, ничего не досчитано за ИИ. */
data class ParsedReceipt(
    val place: String?,
    val date: String?,
    val totalKopecks: Long?,
    val category: String?,
    val lines: List<ReceiptLine>,
)

/** Понятная человеку ошибка разбора чека: текст показывается на экране как есть. */
class ReceiptException(message: String) : Exception(message)

/**
 * Разбор JSON-ответа ИИ о чеке. Суммы читаются через BigDecimal прямо в копейки (без Double),
 * строки без названия и с отрицательной ценой (скидки, возвраты) отбрасываются.
 */
object ReceiptParsing {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Больше этой цены у одной позиции (₽) — почти наверняка ИИ склеил числа. */
    private const val MAX_LINE_RUBLES = 10_000_000L

    fun decode(answer: String): ParsedReceipt {
        val obj = objectIn(answer) ?: throw ReceiptException(BAD_ANSWER)
        return receipt(obj)
    }

    /** По одному результату на снимок: объединение и исключение пересечений делает приложение. */
    fun decodeParts(answer: String, expectedCount: Int): List<ParsedReceipt> {
        val parts = objectIn(answer)?.get("parts") as? JsonArray ?: throw ReceiptException(BAD_ANSWER)
        if (parts.size != expectedCount) throw ReceiptException(BAD_ANSWER)
        return parts.map { receipt(it as? JsonObject ?: throw ReceiptException(BAD_ANSWER)) }
    }

    private fun receipt(obj: JsonObject): ParsedReceipt {
        val lines = (obj["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::line) }
        return ParsedReceipt(
            place = text(obj["store"])?.take(MAX_TEXT),
            date = text(obj["date"])?.takeIf { DATE.matches(it) },
            totalKopecks = kopecks(obj["total"])?.takeIf { it > 0 },
            category = text(obj["category"])?.take(MAX_TEXT),
            lines = lines,
        )
    }

    private fun line(o: JsonObject): ReceiptLine? {
        val name = text(o["name"])?.take(MAX_TEXT) ?: return null
        val price = kopecks(o["sum"]) ?: kopecks(o["price"])
        if (price != null && price < 0) return null
        if (price != null && price > MAX_LINE_RUBLES * 100) return null
        return ReceiptLine(
            name = name,
            priceKopecks = price?.takeIf { it > 0 },
            quantityMilli = quantityMilli(o["qty"]),
            unit = text(o["unit"])?.take(MAX_UNIT),
            pack = PackSize.parse(text(o["pack"])),
        )
    }

    /** Первый JSON-объект в ответе: модель иногда оборачивает его в ```json … ``` или пишет текст вокруг. */
    internal fun objectIn(answer: String): JsonObject? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    private fun text(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.takeIf { e !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /** «1234.5», 1234.5, «1 234,50», «1234,50 ₽» → копейки. */
    internal fun kopecks(e: JsonElement?): Long? {
        val raw = text(e) ?: return null
        return decimal(raw)?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
    }

    private fun quantityMilli(e: JsonElement?): Long? {
        val q = text(e)?.let(::decimal) ?: return null
        return q.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toLong().takeIf { it > 0 }
    }

    private fun decimal(raw: String): BigDecimal? {
        val cleaned = raw.replace(Regex("[\\s\\u00A0₽]|руб\\.?|р\\.", RegexOption.IGNORE_CASE), "").replace(',', '.')
        return cleaned.toBigDecimalOrNull()
    }

    internal const val BAD_ANSWER = "ИИ ответил не в том формате, попробуйте ещё раз"

    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private const val MAX_TEXT = 120
    private const val MAX_UNIT = 10
}

