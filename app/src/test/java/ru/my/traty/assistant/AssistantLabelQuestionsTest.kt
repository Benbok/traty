package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabel
import ru.my.traty.data.labels.ExpenseLabelHit
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantLabelQuestionsTest {
    private val today = LocalDate.of(2026, 11, 22)
    private val categories = listOf(CategoryWithSubs(Category(id = 1, name = "Игры", emoji = "🎮"), emptyList()))

    private fun resolve(question: String, model: AssistantQuery? = null, previous: AssistantQuery? = null): AssistantQuery =
        QuestionRules.resolve(question, model, QuestionRules.guess(question, categories), today, 1, previous, categories)

    @Test fun countsAllImpulsivePurchasesEvenWhenModelMistakesLabelForCategory() {
        val query = resolve("Посчитай все импульсивные покупки", AssistantQuery(
            intent = Intents.TOTAL,
            categories = listOf("импульсивные покупки"),
        ))
        assertEquals(Intents.TOTAL, query.intent)
        assertEquals(listOf(ExpenseLabel.IMPULSIVE), query.labels)
        assertEquals(emptyList<String>(), query.categories)
        assertEquals(QuestionDates.ALL_TIME.toString(), query.from)

        val result = answer(query)
        assertEquals("400 ₽", result.headline)
        assertEquals(setOf(1L, 2L), result.expenseIds.toSet())
        assertTrue(result.facts.contains("покупок: 2"))
        assertFalse(result.facts.contains("Категорий"))
    }

    @Test fun labelCombinesWithCategoryAndPeriodAndRemainsOnFollowUp() {
        val query = resolve("Сколько потратил на импульсивные покупки в категории Игры за неделю?")
        assertEquals(listOf("Игры"), query.categories)
        assertEquals(today.minusDays(6).toString(), query.from)
        assertEquals(emptyList<Long>(), answer(query).expenseIds)

        val followUp = resolve("А в прошлом месяце?", previous = query)
        assertEquals(query.labels, followUp.labels)
        assertEquals(query.categories, followUp.categories)
        assertEquals(listOf(1L), answer(followUp).expenseIds)
    }

    @Test fun recognizesAllApplicationLabelsAndKeepsSubscriptionQuestionsSeparate() {
        val questions = mapOf(
            "Покажи необычно крупные траты" to ExpenseLabel.LARGE,
            "Покажи частые покупки" to ExpenseLabel.FREQUENT,
            "Покажи траты, похожие на подписку" to ExpenseLabel.SUBSCRIPTION,
            "Покажи покупки с меткой «Новое место»" to ExpenseLabel.NEW_PLACE,
        )
        for ((question, label) in questions) {
            val query = resolve(question)
            assertEquals(question, listOf(label), query.labels)
            assertEquals(question, Intents.LIST, query.intent)
            assertEquals(query, AssistantQueries.decode(AssistantQueries.encode(query)))
        }
        assertEquals(Intents.SUBSCRIPTIONS, resolve("Покажи мои подписки").intent)
    }

    private fun answer(query: AssistantQuery): AssistantData {
        val rows = listOf(
            row(1, "Игры", LocalDate.of(2026, 10, 15), 10_000),
            row(2, "Кафе", today, 30_000),
            row(3, "Игры", today, 50_000),
        )
        val hits = setOf(1L, 2L).associateWith { listOf(ExpenseLabelHit(ExpenseLabel.IMPULSIVE, "")) }
        val context = AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories)
        return AssistantEngine.run(AssistantLabelQuestions.attachMatches(query, hits), context)
    }

    private fun row(id: Long, category: String, date: LocalDate, amount: Long) = ExpenseWithCategory(
        expense = Expense(
            id = id,
            categoryId = if (category == "Игры") 1 else 2,
            amountKopecks = amount,
            timestamp = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        ),
        categoryName = category,
        categoryEmoji = "",
        subcategoryName = null,
    )
}
