package ru.my.traty.data.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.items.ExpenseItem

/** Все траты и доходы за всё время: поиск ищет и в прошлых периодах. */
class SearchRepository(private val db: AppDatabase) {
    fun observeExpenses(): Flow<List<ExpenseWithCategory>> = db.expenses().observeAllWithCategory()
    fun observeIncomes(): Flow<List<Income>> = db.incomes().observeAll()

    /** Товары внутри покупок по номеру траты: «шоколад» находит покупку в магазине, где он был одним из товаров. */
    fun observeItems(): Flow<Map<Long, List<ExpenseItem>>> =
        db.expenseItems().observeAll().map { all -> all.groupBy { it.expenseId } }
}
