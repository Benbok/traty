package ru.my.traty.data.purchases

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseGoalDao {
    @Transaction
    @Query("SELECT * FROM purchase_goals WHERE hidden = 0 ORDER BY dueEpochDay, id")
    fun observe(): Flow<List<PurchaseGoalStatus>>

    @Transaction
    @Query("SELECT * FROM purchase_goals WHERE hidden = 0 AND actualPriceKopecks IS NULL ORDER BY dueEpochDay, id")
    suspend fun listActive(): List<PurchaseGoalStatus>

    @Transaction
    @Query("SELECT * FROM purchase_goals WHERE hidden = 1 OR actualPriceKopecks IS NOT NULL")
    fun observeResolved(): Flow<List<PurchaseGoalStatus>>

    @Transaction
    @Query("SELECT * FROM purchase_goals WHERE id = :id")
    suspend fun status(id: Long): PurchaseGoalStatus?

    @Query("SELECT * FROM purchase_contributions WHERE expenseId = :expenseId")
    suspend fun findContribution(expenseId: Long): PurchaseContribution?

    @Insert
    suspend fun insertGoal(goal: PurchaseGoal): Long

    @Update
    suspend fun updateGoal(goal: PurchaseGoal)

    @Insert
    suspend fun insertContribution(contribution: PurchaseContribution)

    @Query("SELECT * FROM purchase_goals ORDER BY id")
    suspend fun getAllGoals(): List<PurchaseGoal>

    @Query("SELECT * FROM purchase_contributions ORDER BY expenseId")
    suspend fun getAllContributions(): List<PurchaseContribution>

    @Insert
    suspend fun insertGoals(goals: List<PurchaseGoal>)

    @Insert
    suspend fun insertContributions(contributions: List<PurchaseContribution>)

    @Query("DELETE FROM purchase_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM purchase_goals")
    suspend fun clearGoals()
}
