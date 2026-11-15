package ru.my.traty.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Доступ к таблице доходов для экранов. Записи сверки удаляются только через «Переделать». */
class IncomeRepository(private val db: AppDatabase) {

    /** Удаляет доход, если его можно править руками; запись сверки не трогает. */
    suspend fun delete(income: Income) {
        if (!income.isUserEditable) return
        withContext(Dispatchers.IO) { db.incomes().delete(income) }
    }
}
