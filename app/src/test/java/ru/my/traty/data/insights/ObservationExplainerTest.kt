package ru.my.traty.data.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationExplainerTest {
    private val o = Observation("up:2:2026-11-01", ObservationKind.CATEGORY_UP, "☕", "«Кафе»: тратите больше обычного", "4 200 ₽ за 14 дней: на 200% больше.")

    @Test
    fun promptCarriesOnlyTheCardText() {
        val prompt = ExplainerPrompt.of(o)
        assertTrue("«Кафе»" in prompt && "4 200 ₽" in prompt)
        // ключ карточки и эмодзи наружу не уходят
        assertFalse("up:2" in prompt)
    }

    @Test
    fun cleanStripsMarkdown() {
        assertEquals("Это **бывает**.".replace("**", ""), ObservationExplainer.clean("  Это **бывает**.\n"))
    }

    @Test
    fun systemPromptForbidsInventingNumbers() {
        assertTrue("Не придумывай цифры" in ObservationExplainer.SYSTEM)
    }
}

private object ExplainerPrompt {
    fun of(o: Observation) = ObservationExplainer.userPrompt(o)
}
