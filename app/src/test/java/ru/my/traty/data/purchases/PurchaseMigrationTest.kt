package ru.my.traty.data.purchases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.Category
import ru.my.traty.data.Expense

@RunWith(RobolectricTestRunner::class)
class PurchaseMigrationTest {
    @Test
    fun migrationPreservesExistingExpensesAndAcceptsNewGoals() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "purchase-migration-test.db"
        context.deleteDatabase(name)
        try {
            val db = AppDatabase.build(context, name)
            try {
                val category = db.categories().insert(Category(name = "Прочее", emoji = "📝"))
                db.expenses().insert(Expense(amountKopecks = 12_300, categoryId = category, timestamp = 15L))
                // Восстанавливаем прежнюю схему накоплений без признака скрытия.
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL("DROP TABLE purchase_contributions")
                sqlite.execSQL("DROP TABLE purchase_goals")
                PurchaseMigrations.MIGRATION_17_18.migrate(sqlite)
                sqlite.execSQL("INSERT INTO purchase_goals (id, title, targetKopecks, dueEpochDay) VALUES (1, 'Наушники', 1200000, 21000)")
                sqlite.execSQL("INSERT INTO purchase_contributions (expenseId, goalId) VALUES (1, 1)")
                sqlite.version = 18
            } finally {
                db.close()
            }

            val migrated = AppDatabase.build(context, name)
            try {
                assertEquals(12_300L, migrated.expenses().getAllWithCategory().single().expense.amountKopecks)
                val goal = migrated.purchaseGoals().listActive().single()
                assertEquals(PurchaseGoal(1, "Наушники", 1_200_000, 21_000), goal.goal)
                assertEquals(12_300L, goal.savedKopecks)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
