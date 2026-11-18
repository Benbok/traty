package ru.my.traty.assistant

import org.junit.Assert.*
import org.junit.Test

class ProductResolutionTest {
    private val option = ItemSuggestion("60 ₽ по чеку", "60 ₽", null, listOf(4L), "Товар")
    private val data = AssistantData("", direct = "Нет точного совпадения", itemSuggestions = listOf(option),
        candidateNames = listOf("Товар", "Другой"), calculateItems = { option })

    @Test fun invalidIdsProduceLocalAlternatives() {
        val result = ProductResolution.answer(data, """{"status":"matched","ids":[0,9]}""")
        assertTrue(result.direct.orEmpty().contains("Возможные совпадения"))
        assertNull(result.headline)
    }

    @Test fun noMatchDoesNotCountAlternatives() {
        val result = ProductResolution.answer(data, """{"status":"none","ids":[]}""")
        assertTrue(result.expenseIds.isEmpty())
        assertEquals(data.direct, result.direct)
    }
}
