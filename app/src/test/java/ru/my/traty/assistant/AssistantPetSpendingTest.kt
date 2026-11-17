package ru.my.traty.assistant

import org.junit.Assert.*
import org.junit.Test
import ru.my.traty.data.*
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantPetSpendingTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val categories = listOf(
        CategoryWithSubs(
            Category(id = 1, name = "Питомцы", emoji = "🐾"),
            listOf(
                Subcategory(id = 1, categoryId = 1, name = "Груминг"),
                Subcategory(id = 2, categoryId = 1, name = "Ветеринар"),
            ),
        ),
    )
    private val ai = AssistantQuery(intent = Intents.TOTAL, categories = listOf("Груминг"), place = "Груминг")

    private fun resolve(question: String, query: AssistantQuery = ai) = QuestionRules.resolve(
        question, query, QuestionRules.guess(question, categories), today, 1, categories = categories,
    )

    @Test
    fun broadQuestionIncludesVetAndGroomingDespiteNarrowAiQuery() {
        val query = resolve("Сколько я потратил на кошку?")
        assertEquals(listOf("Питомцы"), query.categories)
        assertNull(query.place)
        val rows = listOf(
            expense(1, "Питомцы", "Груминг", "Груминг", 150000),
            expense(2, "Питомцы", "Ветеринар", "ЗОАПТЕК", 73100),
            expense(3, "Продукты", null, "Магнит", 50000),
        )
        val result = AssistantEngine.run(
            query, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories),
        )
        assertEquals(setOf(1L, 2L), result.expenseIds.toSet())
        assertEquals("2 231 ₽", result.headline?.replace(' ', ' '))
        assertTrue(result.facts.contains("Питомцы", ignoreCase = true))
    }

    @Test
    fun worksOfflineAndPreservesExplicitDates() {
        val text = "сколько я потратил на кошку в октябре"
        val query = resolve(text, QuestionRules.guess(text, categories)!!)
        assertEquals(listOf("Питомцы"), query.categories)
        assertEquals("2026-10-01", query.from)
        assertEquals("2026-10-31", query.to)
    }

    @Test
    fun explicitServiceFoodAndNamedPetAreNotExpanded() {
        listOf(
            "сколько потратил на груминг кошки",
            "сколько потратил на кошку, только груминг",
            "сколько потратил на корм кошке",
            "сколько потратил на кошку Чуню",
        ).forEach { text ->
            val narrow = AssistantQuery(intent = Intents.TOTAL, categories = listOf("Груминг"))
            assertEquals(text, narrow.categories, resolve(text, narrow).categories)
        }
    }

    @Test
    fun namedShopIsPreserved() {
        val query = resolve("сколько потратил на кошку в ЗОАПТЕК", ai.copy(place = "ЗОАПТЕК"))
        assertEquals(listOf("Питомцы"), query.categories)
        assertEquals("ЗОАПТЕК", query.place)
    }

    @Test
    fun writeCommandsAreNotExpandedAndMissingCategoryIsNotInvented() {
        val delete = ai.copy(intent = Intents.DELETE_EXPENSE, place = null)
        assertEquals(delete.categories, resolve("удали траты на кошку", delete).categories)
        val query = QuestionRules.resolve("сколько потратил на кошку", ai, null, today, 1)
        assertEquals(ai.categories, query.categories)
    }

    private fun expense(id: Long, category: String, sub: String?, place: String, amount: Long) = ExpenseWithCategory(
        Expense(
            id = id, categoryId = 1, amountKopecks = amount, place = place, source = ExpenseSource.VOICE,
            timestamp = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = category, categoryEmoji = "🐾", subcategoryName = sub,
    )
}
