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
}
