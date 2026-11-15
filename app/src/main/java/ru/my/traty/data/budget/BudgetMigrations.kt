package ru.my.traty.data.budget

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Миграции таблиц плана трат. Подключаются в [ru.my.traty.data.AppDatabase.build]. */
object BudgetMigrations {
    /** Планы по категориям и подкатегориям. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `budgets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `subcategoryId` INTEGER,
                    `month` INTEGER NOT NULL,
                    `amountKopecks` INTEGER NOT NULL,
                    `repeats` INTEGER NOT NULL,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_subcategoryId` ON `budgets` (`subcategoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_month` ON `budgets` (`month`)")
        }
    }

    /** Общий лимит на месяц. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `month_budgets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `month` INTEGER NOT NULL,
                    `amountKopecks` INTEGER NOT NULL,
                    `repeats` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_month_budgets_month` ON `month_budgets` (`month`)")
        }
    }
}
