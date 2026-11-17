package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import java.time.LocalDate
import java.time.ZoneOffset

/** «Сколько коктейлей я купил», «сколько гуляша за месяц»: количество с фасовкой считает приложение. */
class AssistantItemQuantityTest {
    private val today = LocalDate.of(2026, 11, 17)
    private var nextId = 1L
    private val items = HashMap<Long, List<ExpenseItem>>()

    private fun exp(rub: Long, day: Int, note: String? = null, vararg goods: ExpenseItem) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = 1, place = "Пятёрочка", note = note, source = ExpenseSource.PHOTO,
                timestamp = LocalDate.of(2026, 11, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = "Продукты", categoryEmoji = "🛒", subcategoryName = null,
        ).also { r -> if (goods.isNotEmpty()) items[r.expense.id] = goods.map { it.copy(expenseId = r.expense.id) } }

    private val rows = listOf(
        exp(
            500, 3, null,
            ExpenseItem(name = "Молочный коктейль", priceKopecks = 27_000, quantityMilli = 3_000, unit = "шт", packMilli = 200_000, packUnit = "мл"),
            ExpenseItem(name = "Хлеб", priceKopecks = 6_000),
        ),
        exp(
            300, 10, null,
            ExpenseItem(name = "Коктейль молочный 450мл", priceKopecks = 12_000),
            ExpenseItem(name = "Говядина гуляш", priceKopecks = 45_700, packMilli = 500_000, packUnit = "г"),
        ),
        // Записано до фасовки: одна трата без списка, вес в заметке
        exp(460, 12, "говядина гуляш 500 г"),
    )

    private val categories = listOf(CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()))

    private fun run(term: String) = AssistantEngine.run(
        AssistantQuery(intent = Intents.TOTAL, categories = listOf(term)),
        AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories, expenseItems = items),
    )

    @Test
    fun shakesInPiecesAndLitres() {
        val facts = run("коктейли").facts
        // 3 шт по 200 мл + 1 шт 450 мл (количество не названо — одна штука)
        assertTrue(facts, "Количество: 4 шт · 1,05 л" in facts)
        assertTrue(facts, "у 1 позиции количество не названо" in facts)
    }

    @Test
    fun goulashWeightIncludesOldNote() {
        val facts = run("гуляш").facts
        assertTrue(facts, "Количество: 2 шт · 1 кг" in facts)
    }

    @Test
    fun listLineShowsPack() {
        val line = AssistantItems.quantity(listOf(ItemPart(0, rows[0].let { items.getValue(it.expense.id).take(1) }, whole = false)))
        assertEquals("Количество: 3 шт · 600 мл.", line)
    }
}
