package ru.my.traty.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseUpgradeTest {
    private val dbName = "upgrade.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** База в формате v1 — SQL взят дословно из app/schemas/.../1.json. */
    private fun createV1Database() {
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`amountKopecks` INTEGER NOT NULL, `currency` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`subcategory` TEXT, `place` TEXT, `note` TEXT, `timestamp` INTEGER NOT NULL, `rawText` TEXT)"
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
                    "`subcategories` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`name`))"
            )
            v1.execSQL("INSERT INTO categories (name, emoji, subcategories, sortOrder) VALUES ('Продукты', '🛒', '', 0)")
            v1.execSQL(
                "INSERT INTO expenses (amountKopecks, currency, category, subcategory, place, note, timestamp, rawText) " +
                    "VALUES (25000, 'RUB', 'Продукты', NULL, NULL, NULL, 1000, NULL)"
            )
            v1.version = 1
        }
    }

    private fun createV2Database() {
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v2 ->
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER, `sortOrder` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)"
            )
            v2.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `subcategories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            v2.execSQL(
                "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`amountKopecks` INTEGER NOT NULL, `currency` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                    "`subcategoryId` INTEGER, `place` TEXT, `note` TEXT, `timestamp` INTEGER NOT NULL, `rawText` TEXT, `source` TEXT NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            v2.execSQL("INSERT INTO categories (id, name, emoji, color, sortOrder, isArchived) VALUES (1, 'Продукты', '🛒', NULL, 0, 0)")
            v2.execSQL(
                "INSERT INTO expenses (id, amountKopecks, currency, categoryId, subcategoryId, place, note, timestamp, rawText, source) " +
                    "VALUES (1, 35000, 'RUB', 1, NULL, 'Магнит', 'Молоко', 1000, NULL, 'manual')"
            )
            v2.version = 2
        }
    }

    @Test
    fun v1DatabaseIsRecreatedAndSeeded() = runTest {
        createV1Database()
        val db = AppDatabase.build(context, dbName)
        try {
            CategoryRepository(db).seedIfEmpty()
            assertTrue(db.expenses().getAllWithCategory().isEmpty())
            assertEquals(25, db.categories().getActiveWithSubs().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2DatabaseMigratesToV3WithoutDataLoss() = runTest {
        createV2Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val expenses = db.expenses().getAllWithCategory()
            assertEquals(1, expenses.size)
            assertEquals(35000L, expenses[0].expense.amountKopecks)

            // Verify incomes table works
            db.incomes().insert(Income(amountKopecks = 5000000, source = "Зарплата", timestamp = 2000))
            val incomes = db.incomes().getAll()
            assertEquals(1, incomes.size)
            assertEquals(5000000L, incomes[0].amountKopecks)
            assertEquals("Зарплата", incomes[0].source)
        } finally {
            db.close()
        }
    }
}
