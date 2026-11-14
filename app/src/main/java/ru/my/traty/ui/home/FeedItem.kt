package ru.my.traty.ui.home

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income

/**
 * Единый элемент ленты: трата или пополнение.
 * Сортируется по timestamp desc.
 */
sealed class FeedItem(val timestamp: Long, val id: String) {
    class ExpenseItem(val data: ExpenseWithCategory) : FeedItem(data.expense.timestamp, "exp_${data.expense.id}")
    class IncomeItem(val data: Income) : FeedItem(data.timestamp, "inc_${data.id}")
}

fun buildFeed(expenses: List<ExpenseWithCategory>, incomes: List<Income>): List<FeedItem> {
    val items = mutableListOf<FeedItem>()
    expenses.mapTo(items) { FeedItem.ExpenseItem(it) }
    incomes.mapTo(items) { FeedItem.IncomeItem(it) }
    items.sortByDescending { it.timestamp }
    return items
}
