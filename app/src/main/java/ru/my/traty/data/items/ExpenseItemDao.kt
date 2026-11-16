package ru.my.traty.data.items

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

private const val SALES =
    "SELECT i.name, i.priceKopecks, i.quantityMilli, i.unit, i.expenseId, e.timestamp, e.place, e.categoryId " +
        "FROM expense_items i JOIN expenses e ON e.id = i.expenseId"

@Dao
interface ExpenseItemDao {
    @Insert
    suspend fun insertAll(items: List<ExpenseItem>)

    @Query("DELETE FROM expense_items WHERE expenseId = :expenseId")
    suspend fun deleteFor(expenseId: Long)

    @Query("SELECT * FROM expense_items WHERE expenseId = :expenseId ORDER BY position, id")
    suspend fun getFor(expenseId: Long): List<ExpenseItem>

    @Query("SELECT * FROM expense_items WHERE expenseId IN (:expenseIds) ORDER BY expenseId, position, id")
    suspend fun getForAll(expenseIds: List<Long>): List<ExpenseItem>

    @Query("SELECT * FROM expense_items WHERE expenseId = :expenseId ORDER BY position, id")
    fun observeFor(expenseId: Long): Flow<List<ExpenseItem>>

    /** Все позиции всех трат: поиск по ленте ищет товары внутри покупок, в том числе прошлых периодов. */
    @Query("SELECT * FROM expense_items ORDER BY expenseId, position, id")
    fun observeAll(): Flow<List<ExpenseItem>>

    /** Сколько товаров в каждой покупке: лента показывает «5 товаров» вместо длинного списка в заметке. */
    @Query("SELECT expenseId, COUNT(*) AS count FROM expense_items GROUP BY expenseId")
    fun observeCounts(): Flow<List<ItemCount>>

    /** Траты, у которых есть позиции: значок «есть товары» в ленте. */
    @Query("SELECT DISTINCT expenseId FROM expense_items")
    fun observeExpenseIdsWithItems(): Flow<List<Long>>

    @Query("$SALES WHERE e.timestamp >= :from AND e.timestamp < :to ORDER BY e.timestamp, i.position")
    suspend fun getSales(from: Long, to: Long): List<ItemSale>

    @Query("$SALES WHERE e.timestamp >= :from AND e.timestamp < :to ORDER BY e.timestamp, i.position")
    fun observeSales(from: Long, to: Long): Flow<List<ItemSale>>
}

object ItemMigrations {
    /** Позиции внутри траты: товар, цена, количество. Старые траты остаются без позиций. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expense_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `expenseId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `priceKopecks` INTEGER,
                    `quantityMilli` INTEGER,
                    `unit` TEXT,
                    FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_items_expenseId` ON `expense_items` (`expenseId`)")
        }
    }
}
