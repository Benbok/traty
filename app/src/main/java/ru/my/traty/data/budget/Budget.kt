package ru.my.traty.data.budget

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.my.traty.data.Category
import ru.my.traty.data.Subcategory

/**
 * План трат (лимит) на категорию или подкатегорию, этап 8.
 *
 * [month] — месяц расчётного периода в виде ГГГГММ (202611): период, который начинается в этом месяце.
 * Так план не съезжает, если в настройках поменять день начала периода.
 *
 * [repeats] = true — план действует и в следующих месяцах, пока его не сменит строка на более поздний месяц.
 * Строка с [amountKopecks] == 0 и [repeats] == true — «с этого месяца без плана» (останавливает повтор).
 *
 * Удаление категории или подкатегории удаляет и её планы (CASCADE): план подкатегории
 * не должен молча превращаться в план всей категории.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Subcategory::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("subcategoryId"), Index("month")],
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val month: Int,
    val amountKopecks: Long,
    val repeats: Boolean = true,
)
