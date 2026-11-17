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

/** «Сколько я потратил на шоколадки»: считается цена самих товаров, а не чеки, в которых они были. */
class AssistantItemsTest {
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L
    private val items = HashMap<Long, List<ExpenseItem>>()

    private fun exp(rub: Long, date: LocalDate, cat: String = "Продукты", place: String? = null, note: String? = null, vararg goods: Pair<String, Long?>) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = 1, place = place, note = note, source = ExpenseSource.VOICE,
                timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = cat, categoryEmoji = "🛒", subcategoryName = null,
        ).also { r ->
            if (goods.isNotEmpty()) {
                items[r.expense.id] = goods.mapIndexed { i, (name, price) ->
                    ExpenseItem(expenseId = r.expense.id, position = i, name = name, priceKopecks = price?.let { it * 100 })
                }
            }
        }

    private fun nov(d: Int) = LocalDate.of(2026, 11, d)

    // В заметке покупки со списком тот же список товаров: раньше по ней засчитывался весь чек
    private val rows = listOf(
        exp(1500, nov(3), place = "Пятёрочка", note = "молоко, хлеб, шоколадка", goods = arrayOf("Молоко" to 100L, "Хлеб" to 60L, "Шоколадка Алёнка" to 120L)),
        exp(900, nov(10), place = "Магнит", note = "шоколад, сыр", goods = arrayOf("Шоколад Ritter Sport" to 150L, "Сыр" to 750L)),
        exp(2000, nov(12), place = "Лента", goods = arrayOf("Шоколадки 3 шт" to 210L, "Курица" to null, "Шоколад горький" to null)),
        exp(400, nov(5), place = "Магнит у дома", goods = arrayOf("Хлеб" to 60L)),
        exp(5000, LocalDate.of(2026, 8, 20), place = "Перекрёсток", goods = arrayOf("Шоколадка" to 90L, "Мясо" to 4910L)),
        exp(80, nov(13), note = "шоколадка"),
    )

    private val categories = listOf(CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()))

    private fun run(q: AssistantQuery) = AssistantEngine.run(
        q, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories, expenseItems = items),
    )

    private fun rub(text: String?) = text?.replace('\u00A0', ' ')?.replace('\u202F', ' ')

    @Test
    fun totalCountsOnlyTheItemsEvenWhenNoteListsThem() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("шоколадки")))
        // Текущий период: 120 + 150 + 210 + 80 (покупка без списка товаров — целиком); Перекрёсток в августе
        assertEquals("560 ₽", rub(d.headline))
        assertEquals(4, d.expenseIds.size)
        assertTrue(d.facts, "позиций 4 в 3 покупках" in d.facts)
        assertTrue(d.facts, "У 1 позиции не записана цена" in d.facts)
        assertTrue(d.facts, "1 покупка без списка товаров посчитана целиком" in d.facts)
        assertTrue(d.facts, "в записях нет" !in d.facts)
    }

    @Test
    fun templateAnswerShowsItemSum() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("шоколад")))
        val text = rub(AssistantAnswers.template(Intents.TOTAL, d)).orEmpty()
        assertTrue(text, "560 ₽" in text)
        assertTrue(text, "цена самих товаров" in text)
    }

    @Test
    fun periodIsApplied() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("шоколадки"), from = "2026-08-01", to = "2026-11-14"))
        assertEquals("650 ₽", rub(d.headline))
        val aug = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("шоколадки"), from = "2026-08-01", to = "2026-08-31"))
        assertEquals("90 ₽", rub(aug.headline))
    }

    @Test
    fun categoryStillCountsWholePurchases() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Продукты")))
        assertEquals("4 880 ₽", rub(d.headline))
        assertTrue(d.facts, "цена самих товаров" !in d.facts)
    }

    @Test
    fun itemWrittenAsPlaceIsSearchedInItems() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, place = "шоколадки"))
        assertEquals("560 ₽", rub(d.headline))
    }

    @Test
    fun itemInsideOneShop() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("шоколад"), place = "Магнит"))
        assertEquals("150 ₽", rub(d.headline))
    }

    @Test
    fun listShowsItemPricesNotReceipts() {
        val d = run(AssistantQuery(intent = Intents.LIST, categories = listOf("шоколадки")))
        val facts = rub(d.facts).orEmpty()
        // Без срока список — за всё время
        assertTrue(facts, "5 на 650 ₽" in facts)
        assertTrue(facts, "Шоколад Ritter Sport 150 ₽, Магнит" in facts)
        assertTrue(facts, "Шоколад горький (без цены)" in facts)
        assertTrue(facts, "1 500 ₽" !in facts)
    }

    @Test
    fun trendAndCompareUseItemPrices() {
        val trend = run(AssistantQuery(intent = Intents.TREND, categories = listOf("шоколадки"), groupBy = GroupBy.MONTH))
        assertTrue(rub(trend.facts), "август 2026: 90 ₽" in rub(trend.facts).orEmpty())
        assertTrue(rub(trend.facts), "560 ₽" in rub(trend.facts).orEmpty())
        val cmp = run(AssistantQuery(intent = Intents.COMPARE, categories = listOf("шоколадки"),
            from = "2026-11-01", to = "2026-11-14", compareFrom = "2026-08-01", compareTo = "2026-08-31"))
        assertEquals("+470 ₽", rub(cmp.headline))
    }

    @Test
    fun receiptWithOnlyThisItemCountsWhole() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("хлеб")))
        // Пятёрочка: 60 ₽ из чека, «Магнит у дома»: в чеке только хлеб — 400 ₽ целиком
        assertEquals("460 ₽", rub(d.headline))
    }

    @Test
    fun itemNameMatchingIgnoresEndings() {
        val i = ExpenseItem(name = "Кока-кола 0,5")
        assertTrue(i.mentionsItem("колу"))
        assertTrue(ExpenseItem(name = "Шоколадка Алёнка").mentionsItem("шоколадки"))
        assertTrue(!ExpenseItem(name = "Сыр").mentionsItem("шоколадки"))
    }
}
