package ru.my.traty.data.purchases

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import ru.my.traty.data.Expense
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "purchase_goals")
data class PurchaseGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetKopecks: Long,
    val dueEpochDay: Long,
    val actualPriceKopecks: Long? = null,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Entity(
    tableName = "purchase_contributions",
    foreignKeys = [
        ForeignKey(entity = Expense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PurchaseGoal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("goalId")],
)
data class PurchaseContribution(@PrimaryKey val expenseId: Long, val goalId: Long)

data class PurchaseGoalStatus(
    @Embedded val goal: PurchaseGoal,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(PurchaseContribution::class, parentColumn = "goalId", entityColumn = "expenseId"),
    )
    val contributions: List<Expense>,
) {
    val savedKopecks: Long get() = contributions.sumOf { it.amountKopecks }
    val due: LocalDate get() = LocalDate.ofEpochDay(goal.dueEpochDay)
    val isActive: Boolean get() = !goal.hidden && goal.actualPriceKopecks == null

    fun remainingToday(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!isActive) return 0
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val beforeToday = contributions.filter { it.timestamp < start }.sumOf { it.amountKopecks }
        val todayTotal = contributions.filter { it.timestamp >= start && it.timestamp < end }.sumOf { it.amountKopecks }
        return (dailyPurchaseAmount(goal.targetKopecks - beforeToday, today, due) - todayTotal).coerceAtLeast(0)
    }

    fun recommendationTomorrow(today: LocalDate, extraKopecks: Long): Long {
        if (!isActive) return 0
        val remaining = (goal.targetKopecks - savedKopecks).coerceAtLeast(0)
        return dailyPurchaseAmount(remaining - extraKopecks.coerceAtLeast(0), today.plusDays(1), due)
    }
}
