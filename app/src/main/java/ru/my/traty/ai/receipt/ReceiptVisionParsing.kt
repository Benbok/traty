package ru.my.traty.ai.receipt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.my.traty.data.items.Pack
import ru.my.traty.data.items.PackSize
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Checks DeepSeek's JSON before any values become editable amounts. */
internal object ReceiptVisionParsing {
    fun decode(answer: String, expectedCount: Int, debugLog: ((String) -> Unit)? = null): List<ParsedReceipt> {
        val root = try {
            Json.parseToJsonElement(answer) as? JsonObject ?: invalid("root is not JsonObject", debugLog)
        } catch (e: IllegalArgumentException) {
            invalid("malformed json: ${e.message}", debugLog)
        }
        val parts = root["parts"] as? JsonArray ?: invalid("missing 'parts' array", debugLog)
        if (parts.size != expectedCount) invalid("parts.size=${parts.size}, expected=$expectedCount", debugLog)
        val receipts = parts.mapIndexed { index, part ->
            receipt(part as? JsonObject ?: invalid("parts[$index] is not JsonObject", debugLog), index, debugLog)
        }
        if (receipts.mapNotNull { it.totalKopecks }.distinct().size > 1) {
            throw ReceiptException("На снимках разные итоги. Проверьте, что это части одного чека")
        }
        if (receipts.all { receipt ->
                receipt.totalKopecks == null && receipt.lines.none { it.name.isNotBlank() || it.priceKopecks != null }
            }
        ) {
            throw ReceiptException("На фото не нашлось товаров или итога. Переснимите чек ближе или заполните вручную")
        }
        return receipts
    }

    private fun receipt(obj: JsonObject, partIndex: Int, debugLog: ((String) -> Unit)?): ParsedReceipt {
        if (text(obj["currency"], 3, "parts[$partIndex].currency", debugLog)?.uppercase() !in listOf(null, "RUB")) {
            throw ReceiptException("На чеке указана другая валюта. Введите сумму покупки в рублях вручную")
        }
        val date = text(obj["date"], 10, "parts[$partIndex].date", debugLog)?.let {
            try {
                LocalDate.parse(it).toString()
            } catch (_: DateTimeParseException) {
                invalid("parts[$partIndex].date invalid ISO date: '$it'", debugLog)
            }
        }
        val items = obj["items"] as? JsonArray ?: invalid("parts[$partIndex].items is not JsonArray", debugLog)
        return ParsedReceipt(
            place = text(obj["store"], 120, "parts[$partIndex].store", debugLog),
            date = date,
            totalKopecks = number(obj["total"], 2, "parts[$partIndex].total", debugLog)?.takeIf { it > 0 },
            category = text(obj["category"], 120, "parts[$partIndex].category", debugLog),
            lines = items.mapIndexed { itemIndex, item ->
                val itemObj = item as? JsonObject ?: invalid("parts[$partIndex].items[$itemIndex] is not JsonObject", debugLog)
                line(itemObj, "parts[$partIndex].items[$itemIndex]", debugLog)
            },
        )
    }

    private fun line(obj: JsonObject, path: String, debugLog: ((String) -> Unit)?) = ReceiptLine(
        name = text(obj["name"], 120, "$path.name", debugLog).orEmpty(),
        priceKopecks = number(obj["sum"], 2, "$path.sum", debugLog),
        quantityMilli = number(obj["qty"], 3, "$path.qty", debugLog)?.takeIf { it > 0 },
        unit = text(obj["unit"], 10, "$path.unit", debugLog),
        pack = pack(obj["pack"]),
        unitPriceKopecks = number(obj["unit_price"], 2, "$path.unit_price", debugLog),
        discountKopecks = number(obj["discount"], 2, "$path.discount", debugLog),
    )

    private fun pack(value: JsonElement?): Pack? {
        val token = value as? JsonPrimitive ?: return null
        if (!token.isString || token.content.length > 30) return null
        return PackSize.parse(token.content)
    }

    private fun text(value: JsonElement?, limit: Int, path: String, debugLog: ((String) -> Unit)?): String? {
        if (value == null || value == JsonNull) return null
        val token = value as? JsonPrimitive ?: invalid("$path is not JsonPrimitive: $value", debugLog)
        if (!token.isString) invalid("$path is not string: $token", debugLog)
        if (token.content.length > limit) invalid("$path exceeds limit $limit (len=${token.content.length}): ${token.content}", debugLog)
        return token.content.trim().ifEmpty { null }
    }

    private fun number(value: JsonElement?, scale: Int, path: String, debugLog: ((String) -> Unit)?): Long? {
        if (value == null || value == JsonNull) return null
        val token = value as? JsonPrimitive ?: invalid("$path is not JsonPrimitive: $value", debugLog)
        if (token.isString) invalid("$path is string instead of number: $token", debugLog)
        if (!decimal.matches(token.content)) invalid("$path does not match decimal: '${token.content}'", debugLog)
        val amount = token.content.toBigDecimalOrNull() ?: invalid("$path not BigDecimal: '${token.content}'", debugLog)
        if (amount < BigDecimal.ZERO || amount > maxAmount) invalid("$path out of range: '${token.content}'", debugLog)
        return amount.movePointRight(scale).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun invalid(reason: String, debugLog: ((String) -> Unit)?): Nothing {
        debugLog?.invoke("parse_failed: $reason")
        throw ReceiptException(BAD_RECEIPT_ANSWER)
    }

    private val decimal = Regex("-?\\d{1,16}(?:\\.\\d{1,8})?")
    private val maxAmount = BigDecimal("100000000")
}
