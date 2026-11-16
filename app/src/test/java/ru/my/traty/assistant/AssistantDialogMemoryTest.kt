package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import java.time.LocalDate
import java.time.ZoneOffset

/** «Удали их» после ответа со списком относится ровно к тратам этого ответа. */
class AssistantDialogMemoryTest {
    private val today = LocalDate.of(2026, 11, 15)
    private var nextId = 1L

    private fun exp(date: LocalDate, rub: Long, cat: Long, name: String, place: String? = null, source: String = ExpenseSource.VOICE) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = cat, place = place, source = source,
                timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = name, categoryEmoji = "🛒", subcategoryName = null,
        )

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Прочее", emoji = "📦"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 3, name = "Кафе и рестораны", emoji = "☕"), emptyList()),
    )

    // 1 — Пятёрочка (прошлый месяц), 2 — сверка, 3 — Пятёрочка, 4 — кафе, 5 — Магнит
    private val rows = listOf(
        exp(LocalDate.of(2026, 10, 3), 500, 2, "Продукты", "Пятёрочка"),
        exp(LocalDate.of(2026, 11, 13), 99_000, 1, "Прочее", source = ExpenseSource.RECONCILE),
        exp(LocalDate.of(2026, 11, 14), 700, 2, "Продукты", "Пятёрочка"),
        exp(LocalDate.of(2026, 11, 14), 300, 3, "Кафе и рестораны"),
        exp(LocalDate.of(2026, 11, 15), 1200, 2, "Продукты", "Магнит"),
    )

    private fun run(q: AssistantQuery) =
        AssistantEngine.run(q, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories))

    private fun correct(question: String, ai: AssistantQuery, previous: AssistantQuery?) =
        AssistantQueries.correct(question, ai, previous, categories)

    @Test
    fun deleteThemTakesExactlyTheShownExpenses() {
        val previous = AssistantQuery(intent = Intents.LIST, categories = listOf("Продукты"), ids = listOf(5, 3, 1))
        val q = correct("удали их", AssistantQuery(intent = Intents.UNKNOWN), previous)
        assertEquals(Intents.DELETE_EXPENSE, q.intent)
        assertEquals(listOf(5L, 3L, 1L), q.ids)
        // Все три, даже старая Пятёрочка за прошлый месяц, которой нет в «текущем периоде»
        assertEquals(setOf(5L, 3L, 1L), run(q).expenseChanges.map { it.id }.toSet())
    }

    @Test
    fun idsWinOverFilterWords() {
        // ИИ положил в запрос «Продукты», но показаны были только траты 4 и 3
        val q = AssistantQuery(intent = Intents.DELETE_EXPENSE, categories = listOf("Продукты"), ids = listOf(4, 3))
        assertEquals(setOf(4L, 3L), run(q).expenseChanges.map { it.id }.toSet())
    }

    @Test
    fun reconcileRowsAreNeverOffered() {
        val q = AssistantQuery(intent = Intents.DELETE_EXPENSE, ids = listOf(2, 3))
        assertEquals(listOf(3L), run(q).expenseChanges.map { it.id })
    }

    @Test
    fun moveSkipsExpensesAlreadyInTarget() {
        val q = AssistantQuery(intent = Intents.MOVE_EXPENSE, target = "Продукты", ids = listOf(3, 4, 5))
        assertEquals(listOf(4L), run(q).expenseChanges.map { it.id })
    }

    @Test
    fun limitStillCutsTheShownList() {
        val q = AssistantQuery(intent = Intents.DELETE_EXPENSE, ids = listOf(5, 4, 3), limit = 1)
        assertEquals(listOf(5L), run(q).expenseChanges.map { it.id })
    }

    @Test
    fun namedFilterIsNotReplacedByPreviousIds() {
        val previous = AssistantQuery(intent = Intents.LIST, ids = listOf(4, 5))
        val q = correct("удали траты в Пятёрочке", AssistantQuery(intent = Intents.DELETE_EXPENSE, place = "Пятёрочка"), previous)
        assertTrue(q.ids.isEmpty())
        assertEquals(setOf(3L, 1L), run(q).expenseChanges.map { it.id }.toSet())
    }

    @Test
    fun withoutPreviousAnswerNothingIsInherited() {
        val q = correct("удали их", AssistantQuery(intent = Intents.UNKNOWN), null)
        assertTrue(q.ids.isEmpty())
    }
}
