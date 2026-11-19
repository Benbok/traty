package ru.my.traty.data.purchases

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PurchaseMigrations {
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE purchase_goals ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS purchase_goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                    targetKopecks INTEGER NOT NULL, dueEpochDay INTEGER NOT NULL, actualPriceKopecks INTEGER
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS purchase_contributions (
                    expenseId INTEGER NOT NULL PRIMARY KEY, goalId INTEGER NOT NULL,
                    FOREIGN KEY(expenseId) REFERENCES expenses(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(goalId) REFERENCES purchase_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_contributions_goalId ON purchase_contributions (goalId)")
        }
    }
}
