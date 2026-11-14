package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income

class FeedItemTest {
    private fun expense(id: Long, ts: Long) = ExpenseWithCategory(
        expense = Expense(id = id, amountKopecks = 100, categoryId = 1, timestamp = ts),
        categoryName = "Еда",
        categoryEmoji = "🍔",
        subcategoryName = null,
    )

    @Test
    fun `лента отсортирована по времени, ключи уникальны, тип строки задан`() {
        val feed = buildFeed(
            expenses = listOf(expense(1, 100), expense(2, 300)),
            incomes = listOf(Income(id = 1, amountKopecks = 500, timestamp = 200)),
        )
        assertEquals(listOf("exp_2", "inc_1", "exp_1"), feed.map { it.id })
        assertEquals(
            listOf(FeedItem.CONTENT_TYPE_EXPENSE, FeedItem.CONTENT_TYPE_INCOME, FeedItem.CONTENT_TYPE_EXPENSE),
            feed.map { it.contentType },
        )
    }
}
