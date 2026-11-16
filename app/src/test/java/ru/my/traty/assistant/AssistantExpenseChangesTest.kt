package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Subcategory
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantExpenseChangesTest {
    private val today = LocalDate.of(2026, 11, 15)
    private var nextId = 1L

    private fun exp(
        date: LocalDate, rub: Long, cat: Long = 1, name: String = "Прочее", sub: String? = null, subId: Long? = null,
        place: String? = null, source: String = ExpenseSource.VOICE,
    ) = ExpenseWithCategory(
        Expense(
            id = nextId++, amountKopecks = rub * 100, categoryId = cat, subcategoryId = subId, place = place, source = source,
            timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = name, categoryEmoji = "🛒", subcategoryName = sub,
    )

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Прочее", emoji = "📦"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 3, name = "Кафе и рестораны", emoji = "☕"), listOf(Subcategory(id = 30, categoryId = 3, name = "Кофе"))),
        CategoryWithSubs(Category(id = 4, name = "Транспорт", emoji = "🚕"), listOf(Subcategory(id = 40, categoryId = 4, name = "Такси"))),
    )

    // Ids по порядку: 1 — старая Пятёрочка, 2 — сверка, 3 — Пятёрочка вчера, 4 — кофе, 5 — последняя
    private val rows = listOf(
        exp(LocalDate.of(2026, 10, 3), 500, place = "Пятёрочка"),
        exp(LocalDate.of(2026, 11, 13), 99_000, source = ExpenseSource.RECONCILE),
        exp(LocalDate.of(2026, 11, 14), 700, place = "Пятёрочка"),
        exp(LocalDate.of(2026, 11, 14), 300, cat = 3, name = "Кафе и рестораны", sub = "Кофе", subId = 30),
        exp(LocalDate.of(2026, 11, 15), 1200, cat = 4, name = "Транспорт", sub = "Такси", subId = 40, place = "Яндекс Такси"),
    )

    private fun run(q: AssistantQuery) =
        AssistantEngine.run(q, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories))

    private fun correct(question: String, ai: AssistantQuery = AssistantQuery(intent = Intents.UNKNOWN), previous: AssistantQuery? = null) =
        AssistantQueries.correct(question, ai, previous, categories)

    @Test
    fun deleteWithoutDetailsOffersOnlyTheLastExpense() {
        val d = run(AssistantQuery(intent = Intents.DELETE_EXPENSE))
        assertEquals(listOf(5L), d.expenseChanges.map { it.id })
        assertEquals(ExpenseAction.DELETE, d.expenseChanges.single().action)
        assertTrue(d.facts, "Пока ничего не изменено" in d.facts)
    }

    @Test
    fun deleteByPlaceSkipsReconcileRows() {
        val d = run(AssistantQuery(intent = Intents.DELETE_EXPENSE, place = "пятёрочке"))
        assertEquals(listOf(3L, 1L), d.expenseChanges.map { it.id })
        val oct = run(AssistantQuery(intent = Intents.DELETE_EXPENSE, place = "пятёрочка", from = "2026-10-01", to = "2026-10-31"))
        assertEquals(listOf(1L), oct.expenseChanges.map { it.id })
        assertTrue(rows[1].expense.id !in d.expenseChanges.map { it.id })
    }

    @Test
    fun limitTakesTheLatestN() {
        val d = run(AssistantQuery(intent = Intents.DELETE_EXPENSE, limit = 2))
        assertEquals(2, d.expenseChanges.size)
        assertEquals(5L, d.expenseChanges.first().id)
    }

    @Test
    fun moveByPlaceToCategory() {
        val d = run(AssistantQuery(intent = Intents.MOVE_EXPENSE, place = "Пятёрочка", target = "Продукты"))
        assertEquals(listOf(3L, 1L), d.expenseChanges.map { it.id })
        val c = d.expenseChanges.first()
        assertEquals(2L, c.toCategoryId)
        assertNull(c.toSubcategoryId)
        assertEquals(1L, c.fromCategoryId)
        assertTrue(c.summary, "Прочее → Продукты" in c.summary)
    }

    @Test
    fun moveToSubcategoryUsesItsCategory() {
        val d = run(AssistantQuery(intent = Intents.MOVE_EXPENSE, limit = 1, target = "такси"))
        // Последняя трата уже в «Такси» — предлагать нечего, берётся следующая подходящая
        assertTrue(d.expenseChanges.isEmpty() || d.expenseChanges.none { it.id == 5L })
        val other = run(AssistantQuery(intent = Intents.MOVE_EXPENSE, place = "Пятёрочка", target = "Такси")).expenseChanges.first()
        assertEquals(4L, other.toCategoryId)
        assertEquals(40L, other.toSubcategoryId)
    }

    @Test
    fun moveWithUnknownOrMissingTargetAsks() {
        assertTrue(run(AssistantQuery(intent = Intents.MOVE_EXPENSE, place = "Пятёрочка", target = "Велосипеды")).facts.contains("Категории «Велосипеды» нет"))
        assertTrue(run(AssistantQuery(intent = Intents.MOVE_EXPENSE, place = "Пятёрочка")).facts.contains("Не сказано, в какую категорию"))
        assertTrue(run(AssistantQuery(intent = Intents.MOVE_EXPENSE, place = "Пятёрочка", target = "Велосипеды")).expenseChanges.isEmpty())
    }

    @Test
    fun nothingFoundSaysSo() {
        val d = run(AssistantQuery(intent = Intents.DELETE_EXPENSE, place = "Магнит"))
        assertTrue(d.expenseChanges.isEmpty())
        assertTrue(d.facts, "Подходящих трат не нашлось" in d.facts)
    }

    @Test
    fun commandsAreRecognisedByRules() {
        val last = correct("удали последнюю трату")
        assertEquals(Intents.DELETE_EXPENSE, last.intent)
        assertEquals(1, last.limit)
        assertEquals(3, correct("удали последние 3 траты").limit)
        val move = correct("перенеси траты в пятерочке в продукты", AssistantQuery(intent = Intents.UNKNOWN, place = "пятерочка"))
        assertEquals(Intents.MOVE_EXPENSE, move.intent)
        assertEquals("Продукты", move.target)
        assertEquals("пятерочка", move.place)
        val byRulesOnly = correct("перенеси траты на такси в транспорт")
        assertEquals("Транспорт", byRulesOnly.target)
        assertEquals(listOf("Такси"), byRulesOnly.categories)
        val longName = correct("перенеси последнюю трату в кафе и рестораны")
        assertEquals("Кафе и рестораны", longName.target)
        assertEquals(1, longName.limit)
    }

    @Test
    fun followUpTakesFiltersFromThePreviousAnswer() {
        val previous = AssistantQuery(intent = Intents.LIST, place = "пятёрочка", from = "2026-10-01", to = "2026-10-31")
        val q = correct("удали эти траты", previous = previous)
        assertEquals(Intents.DELETE_EXPENSE, q.intent)
        assertEquals("пятёрочка", q.place)
        assertEquals("2026-10-01", q.from)
        assertFalse(correct("удали эти траты", previous = AssistantQuery(intent = Intents.PLAN)).place != null)
    }

    @Test
    fun otherCommandsAndQuestionsAreNotExpenseCommands() {
        assertEquals(Intents.REMOVE_PLAN, correct("убери лимит на траты по кафе", AssistantQuery(intent = Intents.PLAN)).intent)
        assertEquals(Intents.CANCEL_SUB, correct("удали платёж за интернет", AssistantQuery(intent = Intents.CANCEL_SUB)).intent)
        assertEquals(Intents.UNKNOWN, correct("как удалить трату?").intent)
        assertEquals(Intents.TOTAL, correct("сколько удалось потратить", AssistantQuery(intent = Intents.TOTAL)).intent)
        // Покупки частями остаются за своими командами
        val debts = AssistantQuery(intent = Intents.DEBTS)
        assertEquals(Intents.DELETE_DEBT, correct("удали эти платежи", AssistantQuery(intent = Intents.CANCEL_SUB), debts).intent)
    }
}
