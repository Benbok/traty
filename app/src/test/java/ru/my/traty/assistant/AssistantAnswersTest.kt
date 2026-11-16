package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAnswersTest {
    private fun data(facts: String, direct: String? = null) = AssistantData(facts = facts, direct = direct)

    @Test fun `числа в разной записи считаются одним числом`() {
        assertEquals(setOf("1500"), AssistantAnswers.numbers("1 500 ₽ и 1500,00"))
        assertEquals(setOf("250.5"), AssistantAnswers.numbers("250,50 и 250.5"))
    }

    @Test fun `придуманное число находится`() {
        val facts = "Потрачено 12 400 ₽ на кафе."
        assertTrue(AssistantAnswers.unknownNumbers("Вы потратили 12 400 ₽ на кафе, 3 раза.", facts).isEmpty())
        assertEquals(listOf("15000"), AssistantAnswers.unknownNumbers("Вы потратили 15 000 ₽", facts))
    }

    @Test fun `непроверенный текст заменяется расчётами`() {
        val d = data("Потрачено 12 400 ₽ на кафе.")
        assertEquals("Всего 12 400 ₽.", AssistantAnswers.verified("Всего 12 400 ₽.", d))
        assertTrue(AssistantAnswers.verified("Всего 99 999 ₽.", d).contains("12 400"))
    }

    @Test fun `шаблон только для простых вопросов и без прямого ответа`() {
        val d = data("Потрачено 12 400 ₽ на кафе.")
        assertTrue(AssistantAnswers.template(Intents.TOTAL, d)!!.contains("12 400"))
        assertNull(AssistantAnswers.template(Intents.BREAKDOWN, d))
        assertNull(AssistantAnswers.template(Intents.TOTAL, data("x", direct = "готово")))
    }

    @Test fun `что ушло в ИИ показывает оба шага`() {
        val both = AssistantAnswers.sent("Вопрос: кафе", listOf("Кафе", "Еда"), "Потрачено 100 ₽")
        assertTrue(both.contains("Шаг 1") && both.contains("Шаг 2") && both.contains("Кафе, Еда"))
        assertTrue(!AssistantAnswers.sent("Вопрос", emptyList(), null).contains("Шаг 2"))
    }
}
