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

class AssistantTrendsTest {
    /** Суббота. */
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun exp(date: LocalDate, rub: Long, cat: String = "Продукты", note: String? = null) = ExpenseWithCategory(
        Expense(
            id = nextId++, amountKopecks = rub * 100, categoryId = 1, note = note, source = ExpenseSource.VOICE,
            timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = cat, categoryEmoji = "🛒", subcategoryName = null,
    )

    private val rows = listOf(
        exp(LocalDate.of(2026, 9, 3), 1000),
        exp(LocalDate.of(2026, 10, 5), 3000),
        exp(LocalDate.of(2026, 10, 20), 500, cat = "Кафе и рестораны"),
        exp(LocalDate.of(2026, 11, 2), 700, cat = "Кафе и рестораны"),
        exp(LocalDate.of(2026, 11, 9), 800, cat = "Кафе и рестораны"),
        exp(LocalDate.of(2026, 11, 14), 400),
    )

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), emptyList()),
    )

    private fun run(q: AssistantQuery) =
        AssistantEngine.run(q, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories))

    @Test
    fun byMonthDefaultsToHalfYearAndMarksCurrentMonth() {
        val d = run(AssistantQuery(intent = Intents.TREND, groupBy = GroupBy.MONTH))
        assertTrue(d.facts, "Траты по месяцам за 1 июня — 14 ноября" in d.facts)
        assertTrue(d.facts, "- октябрь 2026: 3 500 ₽, покупок: 2" in d.facts.replace(' ', ' '))
        assertTrue(d.facts, "- ноябрь 2026 (пока до 14 ноября)" in d.facts)
        assertTrue(d.facts, "- июнь 2026: 0 ₽" in d.facts.replace(' ', ' '))
        assertEquals("больше всего — октябрь 2026", d.caption)
        assertEquals(6, d.expenseIds.size)
    }

    @Test
    fun byWeekSplitsOnMondays() {
        val d = run(AssistantQuery(intent = Intents.TREND, groupBy = GroupBy.WEEK, from = "2026-11-01", to = "2026-11-14"))
        assertTrue(d.facts, "- 1 ноября: 0" in d.facts)
        assertTrue(d.facts, "- 2–8 ноября: 700" in d.facts)
        // Неполные недели в среднее не входят: полная только 2–8 ноября
        assertTrue(d.facts, "В среднем за полный отрезок: 700" in d.facts)
        assertTrue(d.facts, "- 9–14 ноября (пока до 14 ноября): 1 200" in d.facts.replace(' ', ' '))
    }

    @Test
    fun byWeekdayAveragesPerDay() {
        val d = run(AssistantQuery(intent = Intents.TREND, groupBy = GroupBy.WEEKDAY, from = "2026-11-01", to = "2026-11-14"))
        // Понедельников было два (2 и 9 ноября): 700 + 800 = 1500, в среднем 750
        assertTrue(d.facts, "- понедельник: всего 1 500 ₽, в среднем за день 750 ₽" in d.facts.replace(' ', ' '))
        assertEquals("в среднем, понедельник", d.caption)
    }

    @Test
    fun listFindsLastPurchaseOverAllTime() {
        val d = run(AssistantQuery(intent = Intents.LIST, categories = listOf("кафе")))
        assertEquals("9 ноября", d.headline)
        assertTrue(d.facts, "Траты за 20 октября — 14 ноября (Кафе и рестораны): 3" in d.facts.replace(' ', ' ').let { it } || "Траты за 3 сентября — 14 ноября" in d.facts)
        assertTrue(d.facts, "Последняя: 9 ноября, 5 дней назад." in d.facts)
        // 20 октября → 9 ноября: 20 дней на два промежутка
        assertTrue(d.facts, "В среднем раз в 10 дней." in d.facts)
    }

    @Test
    fun listWithNothingFound() {
        val d = run(AssistantQuery(intent = Intents.LIST, from = "2026-11-10", to = "2026-11-12"))
        assertTrue(d.facts, "Таких трат не найдено." in d.facts)
        assertEquals(null, d.headline)
    }

    @Test
    fun allTimeStartsAtFirstRecord() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, from = QuestionDates.ALL_TIME.toString()))
        assertTrue(d.facts, "3 сентября — 14 ноября" in d.facts)
    }
}
