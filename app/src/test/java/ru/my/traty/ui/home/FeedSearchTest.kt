package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.items.ExpenseItem

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

    private fun item(name: String, kopecks: Long? = null) = ExpenseItem(name = name, priceKopecks = kopecks)

    // Покупка в магазине из нескольких товаров, как при диктовке списком
    private val shop = expense(7, "Продукты", "Супермаркет", 798_44, place = "Магнит")
    private val goods = listOf(
        item("яблоки Гала", 184_59),
        item("шоколад Alpen Gold", 209_97),
        item("масло подсолнечное «Слобода»", 139_00),
    )

    private fun hits(q: String) = FeedSearch.matchedItems(goods, q).map { it.name }

    @Test
    fun `покупка находится по товару внутри неё и показывает только найденный`() {
        assertTrue(FeedSearch.matches(shop, "шоколад", goods))
        assertEquals(listOf("шоколад Alpen Gold"), hits("шоколад"))
        // Товара в покупке нет — покупка не находится
        assertFalse(FeedSearch.matches(shop, "носки", goods))
        assertEquals(emptyList<String>(), hits("носки"))
    }

    @Test
    fun `запрос списком ищет любое из слов среди товаров`() {
        assertTrue(FeedSearch.matches(shop, "шоколад масло", goods))
        assertEquals(listOf("шоколад Alpen Gold", "масло подсолнечное «Слобода»"), hits("шоколад масло"))
        // Достаточно одного из слов: «чай» в покупке нет, но шоколад есть
        assertTrue(FeedSearch.matches(shop, "шоколад чай", goods))
        assertEquals(listOf("шоколад Alpen Gold"), hits("шоколад чай"))
        // И через запятую, как говорит человек: «шоколад, масло»
        assertEquals(2, hits("шоколад, масло").size)
    }

    @Test
    fun `слово из записи вместе с товаром считается как все слова вместе`() {
        // «магнит» — место покупки, «шоколад» — товар: покупка найдена, в строке показан только товар
        assertTrue(FeedSearch.matches(shop, "магнит шоколад", goods))
        assertEquals(listOf("шоколад Alpen Gold"), hits("магнит шоколад"))
        // Слова одного и того же текста без товаров дальше требуют всех слов, как раньше
        val pills = expense(5, "Питомцы", "Ветаптека", 640_00, note = "лекарство для кошки Чуни от блох")
        assertTrue(FeedSearch.matches(pills, "лекарство кошке"))
        assertFalse(FeedSearch.matches(pills, "лекарство собаке"))
    }

    @Test
    fun `числа и стоп-слова не считаются найденными товарами`() {
        assertEquals(emptyList<String>(), hits("799"))
        assertEquals(emptyList<String>(), hits(""))
        assertEquals(emptyList<String>(), hits("№"))
        // Сумма по-прежнему ищется по трате
        assertTrue(FeedSearch.matches(shop, "798,44", goods))
    }

    @Test
    fun `без позиций поиск работает как раньше`() {
        // Траты до версии 0.2.1 без разложения на товары: ищем по заметке
        val old = expense(8, "Продукты", "Супермаркет", 500_00, note = "шоколад 150 ₽, молоко 90 ₽")
        assertTrue(FeedSearch.matches(old, "шоколад"))
        assertTrue(FeedSearch.matches(old, "шоколад молоко"))
    }

    @Test
    fun `несколько трат из одной фразы не находят друг друга по словоформам`() {
        // В исходной фразе есть «продукты», но у трат её разобранные поля другие
        assertEquals(emptyList<Long>(), find("продуктов"))
        assertEquals(listOf(3L), find("бензина"))
    }
}
