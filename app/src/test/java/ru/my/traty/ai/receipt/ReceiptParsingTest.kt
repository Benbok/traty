package ru.my.traty.ai.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParsingTest {
    @Test fun decode_fullAnswer_inKopecksWithoutDouble() {
        val r = ReceiptParsing.decode(
            """
            ```json
            {"store": "Пятёрочка", "date": "2026-11-15", "total": "169.99", "category": "Продукты",
             "items": [
               {"name": "Молоко 3,2%", "qty": 1, "unit": "шт", "sum": 89.99},
               {"name": "Хлеб бородинский", "qty": "2", "unit": "шт", "sum": "80,00"},
               {"name": "Скидка", "sum": -10},
               {"name": "", "sum": 5},
               {"name": "Пакет", "sum": null}
             ]}
            ```
            """.trimIndent(),
        )
        assertEquals("Пятёрочка", r.place)
        assertEquals("2026-11-15", r.date)
        assertEquals(16999L, r.totalKopecks)
        assertEquals("Продукты", r.category)
        assertEquals(listOf("Молоко 3,2%", "Хлеб бородинский", "Пакет"), r.lines.map { it.name })
        assertEquals(listOf(8999L, 8000L, null), r.lines.map { it.priceKopecks })
        assertEquals(2000L, r.lines[1].quantityMilli)
    }

    @Test fun decode_badDateAndNulls() {
        val r = ReceiptParsing.decode("""{"store": null, "date": "15.11.2026", "total": null, "items": []}""")
        assertNull(r.place)
        assertNull(r.date)
        assertNull(r.totalKopecks)
        assertEquals(0, r.lines.size)
    }

    @Test fun decode_priceFieldWhenNoSum_andHugeDropped() {
        val r = ReceiptParsing.decode("""{"items": [{"name": "Цемент", "price": "1 250 руб."}, {"name": "Сбой", "sum": 99999999999}]}""")
        assertEquals(listOf("Цемент"), r.lines.map { it.name })
        assertEquals(125000L, r.lines[0].priceKopecks)
    }

    @Test(expected = ReceiptException::class)
    fun decode_notJson() {
        ReceiptParsing.decode("Извините, я не могу прочитать чек")
    }
}
