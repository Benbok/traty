package ru.my.traty.data.budget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Планы трат по категориям (этап 8). Логика выбора действующего плана — [BudgetPlans]. */
@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(item: Budget): Long

    @Update
    suspend fun update(item: Budget)

    @Delete
    suspend fun deleteAll(items: List<Budget>)

    @Query("SELECT * FROM budgets ORDER BY month, id")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY month, id")
    suspend fun getAll(): List<Budget>
}

/** Общий лимит на месяц. Какой действует, решает [TotalBudgets]. */
@Dao
interface MonthBudgetDao {
    @Insert
    suspend fun insert(item: MonthBudget): Long

    @Update
    suspend fun update(item: MonthBudget)

    @Delete
    suspend fun deleteAll(items: List<MonthBudget>)

    @Query("SELECT * FROM month_budgets ORDER BY month, id")
    fun observeAll(): Flow<List<MonthBudget>>

    @Query("SELECT * FROM month_budgets ORDER BY month, id")
    suspend fun getAll(): List<MonthBudget>
}
