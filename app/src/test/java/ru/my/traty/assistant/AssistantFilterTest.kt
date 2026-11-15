package ru.my.traty.assistant

import org.junit.Assert.assertEquals
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

class AssistantFilterTest {
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun exp(rub: Long, cat: String, sub: String? = null, place: String? = null, note: String? = null, day: Int = 10) = ExpenseWithCategory(
        Expense(
            id = nextId++, amountKopecks = rub * 100, categoryId = 1, place = place, note = note, source = ExpenseSource.VOICE,
            timestamp = LocalDate.of(2026, 11, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = cat, categoryEmoji = "🛒", subcategoryName = sub,
    )

    private val rows = listOf(
        exp(1000, "Продукты", place = "Пятёрочка"),
        exp(600, "Продукты", place = "пятерочка у дома"),
        exp(350, "Кафе и рестораны", note = "шаурма у метро"),
        exp(400, "Кафе и рестораны", note = "Шаурма"),
        exp(450, "Кафе и рестораны", sub = "Кофе"),
        exp(1200, "Транспорт", sub = "Такси", place = "Яндекс Такси"),
    )

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), listOf(Subcategory(id = 20, categoryId = 2, name = "Кофе"))),
        CategoryWithSubs(Category(id = 3, name = "Транспорт", emoji = "🚕"), listOf(Subcategory(id = 30, categoryId = 3, name = "Такси"))),
    )

    private fun ctx() = AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories)
    private fun total(q: AssistantQuery) = AssistantEngine.run(q, ctx())

    @Test
    fun placeIgnoresCaseYoAndWordEndings() {
        val d = total(AssistantQuery(intent = Intents.TOTAL, place = "Пятерочке"))
        assertEquals(2, d.expenseIds.size)
        assertEquals("1 600 ₽", d.headline?.replace(' ', ' '))
        assertEquals(1, total(AssistantQuery(intent = Intents.TOTAL, place = "в яндекс такси")).expenseIds.size)
    }

    @Test
    fun wordThatIsNotACategoryIsSearchedInNotes() {
        val d = total(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Шаурму")))
        assertEquals(2, d.expenseIds.size)
        assertTrue(d.facts, "«шаурму»" in d.facts)
        assertTrue(d.facts, "в записях нет" !in d.facts)
    }

    @Test
    fun unknownWordEverywhereStillExplained() {
        val d = total(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Бензин")))
        assertTrue(d.facts, "«Бензин» в записях нет" in d.facts)
        assertTrue(d.expenseIds.isEmpty())
    }

    @Test
    fun categoryWrittenAsPlaceIsTreatedAsCategory() {
        val d = total(AssistantQuery(intent = Intents.PLACES, place = "кофе"))
        assertTrue(d.facts, "(Кофе)" in d.facts)
        val sum = total(AssistantQuery(intent = Intents.TOTAL, place = "кофе"))
        assertEquals(1, sum.expenseIds.size)
    }

    @Test
    fun planCommandKeepsUnknownWordAsNewSubcategory() {
        val d = total(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Шаурма"), amount = 2000.0))
        assertEquals("Шаурма", d.planProposals.single().newSubcategory)
    }

    @Test
    fun stemsDropPrepositionsAndEndings() {
        assertEquals(listOf("янде", "такс"), stems("в Яндекс Такси"))
        assertEquals(listOf("пятероч"), stems("Пятёрочке"))
    }
}
