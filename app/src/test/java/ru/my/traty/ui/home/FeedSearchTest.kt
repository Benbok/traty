package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income

class FeedSearchTest {
    private val phrase = "такси 180 продукты 2.500 бензин 2.160"

    private fun expense(id: Long, cat: String, sub: String?, kopecks: Long, place: String? = null, note: String? = null) =
        ExpenseWithCategory(
            expense = Expense(
                id = id, amountKopecks = kopecks, categoryId = id, timestamp = id,
                place = place, note = note, rawText = phrase,
            ),
            categoryName = cat,
            categoryEmoji = "",
            subcategoryName = sub,
        )

    // Три траты из одной надиктованной фразы, как на скриншоте
    private val taxi = expense(1, "Транспорт", "Такси", 180_00)
    private val food = expense(2, "Еда", "Супермаркет", 2_500_00)
    private val fuel = expense(3, "Автомобиль", "Бензин", 2_160_00, place = "Лукойл")
    private val all = listOf(taxi, food, fuel)

    private fun find(q: String) = all.filter { FeedSearch.matches(it, q) }.map { it.expense.id }

    @Test
    fun `исходная фраза не участвует в поиске`() {
        assertEquals(listOf(3L), find("бензи"))
        assertEquals(listOf(1L), find("такси"))
        assertEquals(emptyList<Long>(), find("продукты"))
    }

    @Test
    fun `ищет по категории, подкатегории, месту и заметке без учёта регистра`() {
        assertEquals(listOf(2L), find("еда"))
        assertEquals(listOf(2L), find("СУПЕР"))
        assertEquals(listOf(3L), find("лукойл"))
        val withNote = expense(4, "Еда", null, 100_00, note = "Кофе с собой")
        assertTrue(FeedSearch.matches(withNote, "кофе"))
    }

    @Test
    fun `пустой запрос показывает всё`() {
        assertEquals(listOf(1L, 2L, 3L), find("  "))
    }

    @Test
    fun `сумма находится с пробелами и без`() {
        assertEquals(listOf(2L), find("2500"))
        assertEquals(listOf(2L), find("2 500"))
        assertEquals(listOf(2L), find("2 500 ₽"))
        assertEquals(listOf(1L), find("180"))
        assertFalse(FeedSearch.amountMatches(2_500_00, "₽"))
    }

    @Test
    fun `доходы ищутся по источнику, заметке и сумме`() {
        val salary = Income(id = 1, amountKopecks = 50_000_00, source = "Зарплата", note = "аванс", timestamp = 1)
        assertTrue(FeedSearch.matches(salary, "зарп"))
        assertTrue(FeedSearch.matches(salary, "аванс"))
        assertTrue(FeedSearch.matches(salary, "50000"))
        assertFalse(FeedSearch.matches(salary, "такси"))
    }

    @Test
    fun `слова запроса ищутся со словоформами в любом порядке`() {
        val pills = expense(5, "Питомцы", "Ветаптека", 640_00, note = "лекарство для кошки Чуни от блох")
        val catFood = expense(6, "Питомцы", "Корм", 900_00, note = "корм для кошки")
        val list = listOf(pills, catFood, taxi)
        fun ids(q: String) = list.filter { FeedSearch.matches(it, q) }.map { it.expense.id }
        assertEquals(listOf(5L), ids("лекарство кошке"))
        assertEquals(listOf(5L), ids("кошке лекарство"))
        assertEquals(listOf(5L, 6L), ids("кошка"))
        assertEquals(listOf(6L), ids("корм кошке"))
        assertEquals(emptyList<Long>(), ids("лекарство собаке"))
    }

    @Test
    fun `число в запросе со словом сверяется с суммой`() {
        assertEquals(listOf(1L), find("такси 180"))
        assertEquals(emptyList<Long>(), find("такси 2500"))
    }

    @Test
    fun `одни знаки без букв и цифр ничего не находят`() {
        assertEquals(emptyList<Long>(), find("₽"))
    }

    @Test
    fun `несколько трат из одной фразы не находят друг друга по словоформам`() {
        // В исходной фразе есть «продукты», но у трат её разобранные поля другие
        assertEquals(emptyList<Long>(), find("продуктов"))
        assertEquals(listOf(3L), find("бензина"))
    }
}
