package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

class QuestionRulesTest {
    private val today = LocalDate.of(2026, 11, 14)

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), listOf(Subcategory(id = 20, categoryId = 2, name = "Кофе"))),
        CategoryWithSubs(Category(id = 3, name = "Транспорт", emoji = "🚕"), listOf(Subcategory(id = 30, categoryId = 3, name = "Такси"))),
    )

    private fun intent(q: String) = QuestionRules.guess(q, categories)?.intent

    @Test
    fun everyKindOfQuestionHasAWay() {
        assertEquals(Intents.TOTAL, intent("Сколько я потратил на такси в октябре?"))
        assertEquals(Intents.PLACES, intent("где больше всего тратил на кофе"))
        assertEquals(Intents.COMPARE, intent("сравни продукты лето и осень"))
        assertEquals(Intents.BREAKDOWN, intent("на что ушли деньги"))
        assertEquals(Intents.TOP, intent("топ 3 покупки за неделю"))
        assertEquals(Intents.TOP, intent("самая дорогая покупка"))
        assertEquals(Intents.AVERAGE, intent("сколько в среднем в день"))
        assertEquals(Intents.SUBSCRIPTIONS, intent("какие у меня подписки"))
        assertEquals(Intents.INCOME, intent("сколько я заработал"))
        assertEquals(Intents.AFFORD, intent("могу ли я купить телефон за 30 тысяч"))
        assertEquals(Intents.PLAN, intent("где перерасход по плану"))
        assertEquals(Intents.BALANCE, intent("сколько денег на счёте"))
        assertEquals(Intents.ADVICE, intent("где я могу сэкономить"))
        assertEquals(Intents.TREND, intent("как менялись траты по месяцам"))
        assertEquals(Intents.LIST, intent("когда я последний раз был в кафе"))
        assertEquals(Intents.DEBTS, intent("сколько я ещё должен"))
        assertEquals(Intents.DEBTS, intent("когда следующий платёж по рассрочке"))
        assertEquals(Intents.TOTAL, intent("сколько я потратил на кредит"))
    }

    @Test
    fun notAboutMoneyStaysUnknown() {
        assertNull(intent("какая завтра погода"))
        assertNull(intent("сколько стоит биткоин"))
        assertNull(intent("расскажи анекдот"))
    }

    @Test
    fun detailsFromTheQuestion() {
        val afford = QuestionRules.guess("могу ли я купить телефон за 30 тысяч", categories)!!
        assertEquals(30000.0, afford.amount!!, 0.0)
        assertEquals(3, QuestionRules.guess("топ-3 покупки", categories)!!.limit)
        assertEquals(GroupBy.WEEKDAY, QuestionRules.guess("в какой день недели я больше трачу", categories)!!.groupBy)
        assertEquals(GroupBy.WEEK, QuestionRules.guess("траты по неделям", categories)!!.groupBy)
    }

    @Test
    fun categoriesNamedInTheQuestion() {
        assertEquals(listOf("Такси"), QuestionRules.categoriesIn(fold("сколько ушло на такси"), categories))
        assertEquals(listOf("Кафе и рестораны"), QuestionRules.categoriesIn(fold("траты в ресторанах"), categories))
        assertEquals(listOf("Продукты"), QuestionRules.categoriesIn(fold("на продукты"), categories))
        assertTrue(QuestionRules.categoriesIn(fold("сколько я потратил"), categories).isEmpty())
    }

    @Test
    fun resolvePrefersAiButFallsBackToRules() {
        val ai = AssistantQuery(intent = Intents.PLACES, categories = listOf("Кофе"))
        val guessed = QuestionRules.guess("где больше всего тратил на кофе", categories)
        assertEquals(ai, QuestionRules.resolve("где больше всего тратил на кофе", ai, guessed, today, 1))
        // ИИ не узнал вопрос или вернул не JSON — работают правила, вместе с датами из вопроса
        val q = "сколько я потратил на такси в октябре"
        val r = QuestionRules.resolve(q, AssistantQuery(intent = Intents.UNKNOWN), QuestionRules.guess(q, categories), today, 1)
        assertEquals(Intents.TOTAL, r.intent)
        assertEquals(listOf("Такси"), r.categories)
        assertEquals("2026-10-01", r.from)
        assertEquals(Intents.UNKNOWN, QuestionRules.resolve("какая погода", null, null, today, 1).intent)
    }

    @Test
    fun fallbackAnswerShowsNumbersWithoutPromptNotes() {
        val data = AssistantData(
            facts = "Сегодня 14 ноября 2026. Суммы в рублях.\nПотрачено: 1 000 ₽.\n" +
                "Записи начинаются с 3 ноября, прошлого периода для сравнения ещё нет — сравнение не приводи.\n",
        )
        val text = AssistantFallback.text(data)
        assertTrue(text, text.startsWith(AssistantFallback.NO_AI))
        assertTrue(text, "Сегодня" !in text)
        assertTrue(text, "не приводи" !in text)
        assertTrue(text, "Потрачено: 1 000 ₽." in text)
        assertTrue(AssistantFallback.text(data, guessed = true, reason = "нет сети").startsWith("ИИ сейчас недоступен (нет сети), "))
    }

    @Test
    fun itemAfterSpentOnIsKeptWithoutAi() {
        assertEquals(listOf("шоколадки"), QuestionRules.subjectIn("сколько я потратил на шоколадки за последний квартал"))
        assertEquals(listOf("молоко"), QuestionRules.subjectIn("сколько ушло на молоко"))
        assertTrue(QuestionRules.subjectIn("сколько я потратил на этой неделе").isEmpty())
        assertTrue(QuestionRules.subjectIn("сколько я потратил в ноябре").isEmpty())
    }

    @Test
    fun howMuchOfItemBoughtWithoutAi() {
        assertEquals(listOf("молока"), QuestionRules.subjectIn("сколько литров молока я купил в ноябре"))
        assertEquals(listOf("коктейлей"), QuestionRules.subjectIn("сколько коктейлей я купил"))
        val q = QuestionRules.guess("Сколько коктейлей я купил за месяц?", emptyList())
        assertEquals(Intents.TOTAL, q?.intent)
        assertEquals(1, q?.categories?.size)
        assertEquals(Intents.LIST, QuestionRules.guess("сколько раз я покупал кофе", emptyList())?.intent)
    }
}
