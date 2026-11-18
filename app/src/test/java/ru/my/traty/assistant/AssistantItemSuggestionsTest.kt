package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantItemSuggestionsTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val query = AssistantQuery(Intents.TOTAL, categories = listOf("шоколадный молочный коктейль"))

    private fun run(q: AssistantQuery, vararg receipts: Triple<String, LocalDate, String>): AssistantData {
        val rows = receipts.mapIndexed { i, (_, date, place) ->
            ExpenseWithCategory(
                Expense(
                    id = i + 1L, amountKopecks = 50_000, categoryId = 1, place = place, source = ExpenseSource.VOICE,
                    timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                ),
                categoryName = "Продукты", categoryEmoji = "", subcategoryName = null,
            )
        }
        val positions = rows.associate { row ->
            val id = row.expense.id
            id to listOf(
                ExpenseItem(expenseId = id, position = 0, name = receipts[id.toInt() - 1].first, priceKopecks = 6_000),
                ExpenseItem(expenseId = id, position = 1, name = "Хлеб", priceKopecks = 44_000),
            )
        }
        return AssistantEngine.run(
            q, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, expenseItems = positions),
        )
    }

    @Test
    fun confirmedFlavorKeepsUnspecifiedAndDifferentFlavorsOutOfTheSum() {
        val result = run(
            query,
            Triple("Коктейль молочный со вкусом шоколада 200мл", today, "Магнит"),
            Triple("Молочный коктейль 200 мл", today, "Магнит"),
            Triple("Молочный коктейль ванильный 200 мл", today, "Магнит"),
        )
        assertTrue(result.itemSuggestions.isEmpty())
        assertEquals(listOf(1L), result.expenseIds)
        assertEquals("60 ₽", result.headline?.replace('\u00A0', ' '))
    }

    @Test
    fun alternativeUsesOnlyNearbyItemLinesInTheRequestedShopAndPeriod() {
        val result = run(
            query.copy(place = "Магнит"),
            Triple("Коктейль молочный 2% 200мл", today, "Магнит"),
            Triple("Коктейль молочный клубничный 200 мл", today, "Магнит"),
            Triple("Молочный коктейль шоколадный 200 мл", today.minusMonths(1), "Магнит"),
            Triple("Молочный коктейль шоколадный 200 мл", today, "Пятёрочка"),
            Triple("Молочный коктейль 200 мл", today, "Пятёрочка"),
        )
        assertNull(result.headline)
        assertTrue(result.expenseIds.isEmpty())
        val suggestion = result.itemSuggestions.single()
        assertEquals(listOf(1L), suggestion.expenseIds)
        assertEquals("60 ₽", suggestion.headline?.replace('\u00A0', ' '))
        assertTrue(suggestion.text, "200 мл" in suggestion.text)
        assertTrue(suggestion.text, "без указания вкуса" in suggestion.text)
    }

    @Test
    fun incompatibleFlavorAndPackDoNotProduceASuggestion() {
        val result = run(
            query.copy(categories = listOf("шоколадный молочный коктейль 200 мл")),
            Triple("Молочный коктейль ванильный 200 мл", today, "Магнит"),
            Triple("Молочный коктейль 500 мл", today, "Магнит"),
        )
        assertTrue(result.itemSuggestions.isEmpty())
        assertTrue(result.direct.orEmpty().contains("не нашёл в чеках"))
    }

    @Test
    fun shortAllTimeQueryPreservesFlavorAndIncludesEarlierPurchases() {
        val question = "шоколадный молочный коктейль за весь период"
        val resolved = QuestionRules.resolve(
            question, query.copy(categories = listOf("молочный коктейль")), QuestionRules.guess(question, emptyList()), today, 1,
        )
        assertEquals(query.categories, resolved.categories)
        assertEquals(QuestionDates.ALL_TIME.toString(), resolved.from)
        assertEquals(today.toString(), resolved.to)
        assertEquals(listOf(1L), run(resolved, Triple("Молочный коктейль шоколадный 200 мл", today.minusMonths(1), "Магнит")).expenseIds)
        assertEquals(resolved, QuestionRules.resolve(question, null, QuestionRules.guess(question, emptyList()), today, 1))
    }
}
