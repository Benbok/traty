package ru.my.traty.data

import ru.my.traty.data.apartments.*
import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.BudgetDao
import ru.my.traty.data.budget.BudgetMigrations
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.budget.MonthBudgetDao
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtDao
import ru.my.traty.data.debt.DebtMigrations
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ExpenseItemDao
import ru.my.traty.data.items.ItemMigrations
import ru.my.traty.data.weekly.WeeklyMigrations
import ru.my.traty.data.weekly.WeeklyTask
import ru.my.traty.data.weekly.WeeklyTaskDao

@Database(
    entities = [Apartment::class, Meter::class, MeterRegister::class, MeterReading::class, ReadingSubmission::class, SubmissionReading::class, Expense::class, Category::class, Subcategory::class, Income::class, Reconciliation::class, Template::class, Rule::class, PendingPhrase::class, Recurring::class, Budget::class, MonthBudget::class, WeeklyTask::class, Debt::class, DebtPayment::class, ExpenseItem::class],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apartments(): ApartmentDao
    abstract fun expenses(): ExpenseDao
    abstract fun categories(): CategoryDao
    abstract fun incomes(): IncomeDao
    abstract fun reconciliations(): ReconciliationDao
    abstract fun templates(): TemplateDao
    abstract fun rules(): RuleDao
    abstract fun pendingPhrases(): PendingPhraseDao
    abstract fun recurring(): RecurringDao
    abstract fun budgets(): BudgetDao
    abstract fun monthBudgets(): MonthBudgetDao
    abstract fun weeklyTasks(): WeeklyTaskDao
    abstract fun debts(): DebtDao
    abstract fun expenseItems(): ExpenseItemDao

    companion object {
        val MIGRATION_2_3 = LegacyMigrations.MIGRATION_2_3
        val MIGRATION_3_4 = LegacyMigrations.MIGRATION_3_4
        val MIGRATION_4_5 = LegacyMigrations.MIGRATION_4_5
        val MIGRATION_5_6 = LegacyMigrations.MIGRATION_5_6
        val MIGRATION_6_7 = LegacyMigrations.MIGRATION_6_7
        val MIGRATION_7_8 = LegacyMigrations.MIGRATION_7_8
        val MIGRATION_8_9 = LegacyMigrations.MIGRATION_8_9
        val MIGRATION_9_10 = LegacyMigrations.MIGRATION_9_10

        fun build(context: Context, name: String = "traty.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, BudgetMigrations.MIGRATION_10_11, BudgetMigrations.MIGRATION_11_12, WeeklyMigrations.MIGRATION_12_13, DebtMigrations.MIGRATION_13_14, ItemMigrations.MIGRATION_14_15, ItemMigrations.MIGRATION_15_16, ApartmentMigrations.MIGRATION_16_17)
                // Решение владельца: в v1 реальных данных нет — базу v1 пересоздаём.
                // Для версий 2+ — только Migration(n, n+1).
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}
