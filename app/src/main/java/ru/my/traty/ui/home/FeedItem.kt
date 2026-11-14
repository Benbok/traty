package ru.my.traty.ui.home

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income

/**
 * Единый элемент ленты: трата или пополнение.
 * Сортируется по timestamp desc.
 */
sealed class FeedItem(val timestamp: Long, val id: String) {
    /** Тип строки для LazyColumn: строки одного типа переиспользуют друг друга при прокрутке. */
    abstract val contentType: String

    class ExpenseItem(val data: ExpenseWithCategory) : FeedItem(data.expense.timestamp, "exp_${data.expense.id}") {
        override val contentType: String get() = CONTENT_TYPE_EXPENSE
    }
    class IncomeItem(val data: Income) : FeedItem(data.timestamp, "inc_${data.id}") {
        override val contentType: String get() = CONTENT_TYPE_INCOME
    }

    companion object {
        const val CONTENT_TYPE_EXPENSE = "expense"
        const val CONTENT_TYPE_INCOME = "income"
    }
}

fun buildFeed(expenses: List<ExpenseWithCategory>, incomes: List<Income>): List<FeedItem> {
    val items = mutableListOf<FeedItem>()
    expenses.mapTo(items) { FeedItem.ExpenseItem(it) }
    incomes.mapTo(items) { FeedItem.IncomeItem(it) }
    items.sortByDescending { it.timestamp }
    return items
}
