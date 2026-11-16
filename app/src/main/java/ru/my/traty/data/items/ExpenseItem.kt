package ru.my.traty.data.items

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.my.traty.data.Expense

/**
 * Позиция внутри траты: «Эльдорадо, 45 000 ₽» = телефон 40 000 + чехол 2 000 + клавиатура 3 000.
 * Трата остаётся одной записью со своей суммой, позиции только раскладывают её по товарам.
 * Удаление траты удаляет и её позиции.
 *
 * [priceKopecks] — сколько заплачено за позицию целиком (за все штуки), в копейках; null — цену не назвали.
 * [quantityMilli] — количество в тысячных: 2 шт = 2000, 0,5 кг = 500; null — не названо.
 * [unit] — «шт», «кг», «л» или null. [position] — порядок в чеке, с нуля.
 */
@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(entity = Expense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("expenseId")],
)
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long = 0,
    val position: Int = 0,
    val name: String,
    val priceKopecks: Long? = null,
    val quantityMilli: Long? = null,
    val unit: String? = null,
)

/** Сколько товаров в одной покупке (трате с позициями). */
data class ItemCount(val expenseId: Long, val count: Int)

/**
 * Позиция вместе с данными её траты: для подсчёта по товарам за период и внутри магазина.
 * Читается одним запросом ([ExpenseItemDao.getSales]).
 */
data class ItemSale(
    val name: String,
    val priceKopecks: Long?,
    val quantityMilli: Long?,
    val unit: String?,
    val expenseId: Long,
    val timestamp: Long,
    val place: String?,
    val categoryId: Long,
)
