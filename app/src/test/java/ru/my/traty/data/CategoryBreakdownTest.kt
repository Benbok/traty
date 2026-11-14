package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBreakdownTest {
    private var nextId = 1L

    private fun e(
        cat: Long,
        rub: Long,
        ts: Long,
        sub: Pair<Long, String>? = null,
        place: String? = null,
        note: String? = null,
    ) = ExpenseWithCategory(
        expense = Expense(
            id = nextId++,
            amountKopecks = rub * 100,
            categoryId = cat,
            subcategoryId = sub?.first,
            place = place,
            note = note,
            timestamp = ts,
        ),
        categoryName = "c$cat",
        categoryEmoji = "",
        subcategoryName = sub?.second,
    )

    @Test
    fun autoSplitsBySubcategoriesFirst() {
        val fuel = 10L to "Бензин"
        val wash = 11L to "Мойка"
        val b = CategoryBreakdowns.build(
            listOf(
                e(1, 3000, 1, fuel, "Лукойл"),
                e(1, 2500, 3, fuel, "Газпромнефть"),
                e(1, 800, 2, wash),
                e(1, 5000, 4, note = "ремонт"),
            )
        ).getValue(1)

        assertEquals(11300_00, b.total)
        assertEquals(listOf(BreakdownMode.SUBCATEGORIES, BreakdownMode.STORES, BreakdownMode.EXPENSES), b.modes)
        assertEquals(BreakdownMode.SUBCATEGORIES, b.defaultMode)
        // По сумме, «Без подкатегории» — последним даже при большей сумме
        assertEquals(listOf("Бензин", "Мойка", CategoryBreakdowns.NO_SUBCATEGORY), b.bySubcategory.map { it.name })
        assertEquals(listOf(5500_00L, 800_00L, 5000_00L), b.bySubcategory.map { it.total })
        assertEquals(2, b.bySubcategory[0].count)
        // Траты внутри группы — новые сверху
        assertEquals(listOf(3L, 1L), b.bySubcategory[0].expenses.map { it.expense.timestamp })
        assertEquals(listOf(4L, 3L, 2L, 1L), b.expenses.map { it.expense.timestamp })
    }

    @Test
    fun groceriesWithoutSubcategoriesStartWithStores() {
        val b = CategoryBreakdowns.build(
            listOf(
                e(2, 500, 1, place = "магазин Пятёрочка"),
                e(2, 700, 2, place = "пятерочка"),
                e(2, 300, 3, place = "Пятёрочка"),
                e(2, 900, 4, place = "Магнит"),
                e(2, 100, 5),
            )
        ).getValue(2)

        assertFalse(b.hasSubcategories)
        assertEquals(listOf(BreakdownMode.STORES, BreakdownMode.EXPENSES), b.modes)
        assertEquals(listOf("Пятёрочка", "Магнит", Places.NO_PLACE), b.byStore.map { it.name })
        assertEquals(1500_00L, b.byStore[0].total)
        assertEquals(3, b.byStore[0].count)
        assertTrue(b.byStore.last().isUnassigned)
    }

    @Test
    fun categoryWithoutSubsAndStoresIsJustAList() {
        val b = CategoryBreakdowns.build(listOf(e(3, 100, 1, note = "подарок маме"), e(3, 50, 2))).getValue(3)
        assertEquals(listOf(BreakdownMode.EXPENSES), b.modes)
        assertEquals(BreakdownMode.EXPENSES, b.defaultMode)
        assertTrue(b.groups(BreakdownMode.EXPENSES).isEmpty())
    }

    @Test
    fun categoriesAreSeparated() {
        val m = CategoryBreakdowns.build(listOf(e(1, 1, 1), e(2, 2, 2), e(1, 3, 3)))
        assertEquals(setOf(1L, 2L), m.keys)
        assertEquals(400L, m.getValue(1).total)
    }

    @Test
    fun deletedSubcategoryCountsAsNone() {
        // Подкатегорию удалили: id остался, а названия нет (LEFT JOIN дал null)
        val b = CategoryBreakdowns.build(listOf(e(1, 100, 1, sub = 99L to ""), e(1, 50, 2))).getValue(1)
        assertEquals(1, b.bySubcategory.size)
        assertFalse(b.hasSubcategories)
    }

    @Test
    fun titlePrefersNoteThenSubcategoryThenPlace() {
        assertEquals("ремонт", CategoryBreakdowns.title(e(1, 1, 1, 10L to "ТО", "Сервис", note = "ремонт")))
        assertEquals("ТО", CategoryBreakdowns.title(e(1, 1, 1, 10L to "ТО", "Сервис")))
        assertEquals("Сервис", CategoryBreakdowns.title(e(1, 1, 1, place = "магазин сервис")))
        assertEquals("", CategoryBreakdowns.title(e(1, 1, 1)))
    }
}
