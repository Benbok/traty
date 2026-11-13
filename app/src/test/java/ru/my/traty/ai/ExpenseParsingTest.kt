package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseParsingTest {
    @Test
    fun decodesObjectInsideCodeFence() {
        val r = ExpenseParsing.decode(
            "```json\n{\"items\":[{\"amount\":250,\"category\":\"Кафе и рестораны\"},{\"amount\":400,\"category\":\"Транспорт\"}]}\n```"
        )
        assertEquals(listOf(250.0, 400.0), r.map { it.amount })
        assertEquals("Транспорт", r[1].category)
    }

    @Test
    fun decodesBareArrayAndDropsNonPositive() {
        val r = ExpenseParsing.decode("[{\"amount\":0},{\"amount\":99.5,\"emoji\":\"🐶\",\"category\":\"Питомцы\"}]")
        assertEquals(1, r.size)
        assertEquals("🐶", r[0].emoji)
    }
}
