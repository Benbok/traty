package ru.my.traty.ai.receipt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.my.traty.data.items.PackSize
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Checks DeepSeek's JSON before any values become editable amounts. */
internal object ReceiptVisionParsing {
    fun decode(answer: String, expectedCount: Int): List<ParsedReceipt> {
        val root = try {
            Json.parseToJsonElement(answer) as? JsonObject ?: invalid()
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        val parts = root["parts"] as? JsonArray ?: invalid()
        if (parts.size != expectedCount) invalid()
        val receipts = parts.map { receipt(it as? JsonObject ?: invalid()) }
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

    private fun receipt(obj: JsonObject): ParsedReceipt {
        if (text(obj["currency"], 3)?.uppercase() !in listOf(null, "RUB")) {
            throw ReceiptException("На чеке указана другая валюта. Введите сумму покупки в рублях вручную")
        }
        val date = text(obj["date"], 10)?.let {
            try {
                LocalDate.parse(it).toString()
            } catch (_: DateTimeParseException) {
                invalid()
            }
        }
        return ParsedReceipt(
            place = text(obj["store"]),
            date = date,
            totalKopecks = number(obj["total"], 2)?.takeIf { it > 0 },
            category = text(obj["category"]),
            lines = (obj["items"] as? JsonArray ?: invalid()).map { line(it as? JsonObject ?: invalid()) },
        )
    }

    private fun line(obj: JsonObject) = ReceiptLine(
        name = text(obj["name"]).orEmpty(),
        priceKopecks = number(obj["sum"], 2),
        quantityMilli = number(obj["qty"], 3)?.takeIf { it > 0 },
        unit = text(obj["unit"], 10),
        pack = PackSize.parse(text(obj["pack"], 30)),
        unitPriceKopecks = number(obj["unit_price"], 2),
        discountKopecks = number(obj["discount"], 2),
    )

    private fun text(value: JsonElement?, limit: Int = 120): String? {
        if (value == null || value == JsonNull) return null
        val token = value as? JsonPrimitive ?: invalid()
        if (!token.isString || token.content.length > limit) invalid()
        return token.content.trim().ifEmpty { null }
    }

    private fun number(value: JsonElement?, scale: Int): Long? {
        if (value == null || value == JsonNull) return null
        val token = value as? JsonPrimitive ?: invalid()
        if (token.isString || !decimal.matches(token.content)) invalid()
        val amount = token.content.toBigDecimalOrNull() ?: invalid()
        if (amount < BigDecimal.ZERO || amount > maxAmount) invalid()
        return amount.movePointRight(scale).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun invalid(): Nothing = throw ReceiptException(BAD_RECEIPT_ANSWER)

    private val decimal = Regex("-?\\d{1,16}(?:\\.\\d{1,8})?")
    private val maxAmount = BigDecimal("100000000")
}
