package ru.my.traty.ai.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.capture.ReceiptParts
import ru.my.traty.data.AppSettings
import ru.my.traty.data.canScanReceipts

class ReceiptVisionParsingTest {
    @Test fun deepSeekKeyAloneEnablesReceiptScanning() {
        assertEquals(true, AppSettings(deepseekKey = "test", deepseekModel = "").canScanReceipts)
    }

    @Test fun preservesPaidLineTotalQuantityAndDiscountAsExactValues() {
        val result = decode("""{"store":"Магазин","date":"2026-11-23","total":188.88,"items":[
            {"name":"Бананы","qty":1.234,"unit":"кг","unit_price":199.00,"discount":56.69,"sum":188.88},
            {"name":null,"qty":null,"sum":null}]}""")
        val line = result.lines.first()
        assertEquals(18_888L, line.priceKopecks)
        assertEquals(1_234L, line.quantityMilli)
        assertEquals(19_900L, line.unitPriceKopecks)
        assertEquals(5_669L, line.discountKopecks)
        assertEquals("", result.lines.last().name)
        assertNull(result.lines.last().priceKopecks)
    }

    @Test(expected = ReceiptException::class)
    fun rejectsMalformedItemsInsteadOfDroppingThem() {
        decode("""{"items":[{"name":"Хлеб","sum":true}]}""")
    }

    @Test(expected = ReceiptException::class)
    fun rejectsMoneyOutsideLongRange() {
        decode("""{"items":[{"name":"Хлеб","sum":184467440737095516.17}]}""")
    }

    @Test(expected = ReceiptException::class)
    fun rejectsMixedReceiptsWithConflictingTotals() {
        ReceiptVisionParsing.decode(
            """{"parts":[{"total":100,"items":[]},{"total":200,"items":[]}]}""",
            expectedCount = 2,
        )
    }

    @Test fun ambiguousSingleLineAtBoundaryRemainsForReview() {
        val first = decode("""{"items":[{"name":"Молоко","sum":80},{"name":"Хлеб","sum":40}]}""")
        val second = decode("""{"items":[{"name":"Хлеб","sum":40},{"name":"Сыр","sum":100}]}""")
        val merged = ReceiptParts.merge(listOf(first, second))
        assertEquals(listOf("Молоко", "Хлеб", "Хлеб", "Сыр"), merged.receipt.lines.map { it.name })
        assertEquals(1, merged.notices.size)
    }

    @Test fun multiLineOverlapIsRemovedWhileRepeatedPurchaseIsPreserved() {
        val first = decode("""{"items":[{"name":"Молоко","sum":80},{"name":"Хлеб","sum":40},{"name":"Сыр","sum":100}]}""")
        val second = decode("""{"items":[{"name":"Хлеб","sum":40},{"name":"Сыр","sum":100},{"name":"Хлеб","sum":40}]}""")
        val merged = ReceiptParts.merge(listOf(first, second))
        assertEquals(listOf("Молоко", "Хлеб", "Сыр", "Хлеб"), merged.receipt.lines.map { it.name })
    }

    @Test fun multiplePossibleOverlapsRemainForReview() {
        val first = decode("""{"items":[{"name":"Хлеб","sum":40},{"name":"Сыр","sum":100},
            {"name":"Хлеб","sum":40},{"name":"Сыр","sum":100}]}""")
        val second = decode("""{"items":[{"name":"Хлеб","sum":40},{"name":"Сыр","sum":100},
            {"name":"Хлеб","sum":40},{"name":"Сыр","sum":100},{"name":"Молоко","sum":80}]}""")
        val merged = ReceiptParts.merge(listOf(first, second))
        assertEquals(0, merged.removed)
        assertEquals(9, merged.receipt.lines.size)
    }

    @Test fun ignoresNumericPackWithoutFailingReceipt() {
        val result = decode(
            """{"store":"Магнит","date":null,"currency":"RUB","category":"Продукты","total":841.33,"items":[
                {"name":"ПОСИДЕЛКИНО печенье овсяное классич. 320г","qty":1,"unit":"шт","pack":0.32,"unit_price":119.99,"discount":30,"sum":89.99},
                {"name":"HEINZ Кетчуп томатный 550г д/п «Петропро","qty":1,"unit":"шт","pack":"550 г","unit_price":239.99,"discount":47,"sum":192.99}
            ]}""",
        )
        assertEquals(2, result.lines.size)
        assertNull(result.lines[0].pack)
        assertEquals(550_000L, result.lines[1].pack?.milli)
        assertEquals("г", result.lines[1].pack?.unit)
    }

    @Test fun logsExactFieldReasonWhenInvalid() {
        val logged = mutableListOf<String>()
        val error = runCatching {
            ReceiptVisionParsing.decode(
                """{"parts":[{"items":[{"name":"Хлеб","sum":"40"}]}]}""",
                expectedCount = 1,
                debugLog = { logged += it },
            )
        }.exceptionOrNull()
        assertEquals(BAD_RECEIPT_ANSWER, error?.message)
        assertEquals(listOf("parse_failed: parts[0].items[0].sum is string instead of number: \"40\""), logged)
    }

    private fun decode(part: String) = ReceiptVisionParsing.decode("""{"parts":[$part]}""", 1).single()
}
