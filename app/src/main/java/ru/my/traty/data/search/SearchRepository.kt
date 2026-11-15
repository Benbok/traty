package ru.my.traty.data.search

import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income

/** Все траты и доходы за всё время: поиск ищет и в прошлых периодах. */
class SearchRepository(private val db: AppDatabase) {
    fun observeExpenses(): Flow<List<ExpenseWithCategory>> = db.expenses().observeAllWithCategory()
    fun observeIncomes(): Flow<List<Income>> = db.incomes().observeAll()
}
