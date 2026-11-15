package ru.my.traty.data.budget

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Общий лимит трат на расчётный месяц (этап 8): сколько всего можно потратить, по всем категориям вместе.
 *
 * Правила те же, что у [Budget]: [month] — ГГГГММ месяца, в котором начинается расчётный период;
 * [repeats] = true — лимит действует и дальше, пока его не сменят; строка с суммой 0 и [repeats] —
 * «с этого месяца без общего лимита».
 */
@Entity(tableName = "month_budgets", indices = [Index("month")])
data class MonthBudget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: Int,
    val amountKopecks: Long,
    val repeats: Boolean = true,
)
