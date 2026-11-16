package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import java.time.LocalDate
import java.time.ZoneOffset

/** Название магазина в разных написаниях: «Озон» в вопросе находит «Ozon» в записях и наоборот. */
class AssistantBrandTest {
    private fun exp(id: Long, rub: Long, place: String) = ExpenseWithCategory(
        Expense(
            id = id, amountKopecks = rub * 100, categoryId = 1, place = place, source = ExpenseSource.VOICE,
            timestamp = LocalDate.of(2026, 11, 10).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = "Покупки", categoryEmoji = "🛍", subcategoryName = null,
    )

    private val rows = listOf(exp(1, 1000, "Ozon"), exp(2, 500, "Wildberries"), exp(3, 300, "Магнит"))
    private val categories = listOf(CategoryWithSubs(Category(id = 1, name = "Покупки", emoji = "🛍"), emptyList()))
    private fun total(q: AssistantQuery) =
        AssistantEngine.run(q, AssistantContext(rows, emptyList(), emptyList(), LocalDate.of(2026, 11, 14), 1, ZoneOffset.UTC, categories = categories))

    @Test fun russianNameFindsLatinPlace() {
        assertEquals(listOf(1L), total(AssistantQuery(intent = Intents.TOTAL, place = "Озон")).expenseIds)
        assertEquals(listOf(2L), total(AssistantQuery(intent = Intents.TOTAL, place = "ВБ")).expenseIds)
    }

    @Test fun unknownWordInCategoriesIsSearchedAsBrand() {
        assertEquals(listOf(1L), total(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Озон"))).expenseIds)
    }

    @Test fun otherPlacesAreNotMixedIn() {
        assertEquals(listOf(3L), total(AssistantQuery(intent = Intents.TOTAL, place = "Магнит")).expenseIds)
    }
}
