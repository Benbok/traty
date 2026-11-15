package ru.my.traty.data.debt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Платёж по покупке вместе с датой и суммой его траты. */
data class DebtPaymentRow(
    val debtId: Long,
    val expenseId: Long,
    val part: String,
    val amountKopecks: Long,
    val timestamp: Long,
)

@Dao
interface DebtDao {
    @Insert
    suspend fun insert(item: Debt): Long

    @Update
    suspend fun update(item: Debt)

    @Insert
    suspend fun insertPayment(item: DebtPayment): Long

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): Debt?

    @Query("SELECT * FROM debts WHERE state = 'active' ORDER BY createdAt DESC")
    suspend fun getActive(): List<Debt>

    @Query("SELECT * FROM debts ORDER BY state = 'active' DESC, createdAt DESC")
    fun observeAll(): Flow<List<Debt>>

    @Query(
        "SELECT p.debtId, p.expenseId, p.part, e.amountKopecks, e.timestamp FROM debt_payments p " +
            "JOIN expenses e ON e.id = p.expenseId WHERE p.debtId = :debtId ORDER BY e.timestamp"
    )
    suspend fun getPayments(debtId: Long): List<DebtPaymentRow>

    @Query(
        "SELECT p.debtId, p.expenseId, p.part, e.amountKopecks, e.timestamp FROM debt_payments p " +
            "JOIN expenses e ON e.id = p.expenseId ORDER BY e.timestamp"
    )
    fun observePayments(): Flow<List<DebtPaymentRow>>
}

object DebtMigrations {
    /** Покупки частями (аванс и доплаты, рассрочка, кредит) и связь их платежей с тратами. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `debts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `place` TEXT,
                    `categoryId` INTEGER NOT NULL,
                    `subcategoryId` INTEGER,
                    `kind` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `currency` TEXT NOT NULL,
                    `totalKopecks` INTEGER,
                    `rateBasisPoints` INTEGER NOT NULL,
                    `paymentsCount` INTEGER,
                    `periodUnit` TEXT NOT NULL,
                    `periodEvery` INTEGER NOT NULL,
                    `firstDueEpochDay` INTEGER,
                    `paymentKopecks` INTEGER,
                    `state` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_state` ON `debts` (`state`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `debt_payments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `debtId` INTEGER NOT NULL,
                    `expenseId` INTEGER NOT NULL,
                    `part` TEXT NOT NULL,
                    FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debtId` ON `debt_payments` (`debtId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_payments_expenseId` ON `debt_payments` (`expenseId`)")
        }
    }
}
