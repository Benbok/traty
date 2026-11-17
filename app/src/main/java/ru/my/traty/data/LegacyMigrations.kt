package ru.my.traty.data

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

/** Прежние миграции, SQL сохранён без изменений. */
internal object LegacyMigrations {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `incomes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountKopecks` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `note` TEXT,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_incomes_timestamp` ON `incomes` (`timestamp`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `incomes` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reconciliations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `actualKopecks` INTEGER NOT NULL, `expectedKopecks` INTEGER, " +
                        "`differenceKopecks` INTEGER NOT NULL, `expenseId` INTEGER, `incomeId` INTEGER, `isOpening` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_timestamp` ON `reconciliations` (`timestamp`)")
                // Сравнение имён без учёта регистра — в Kotlin (sameName); вариант пользователя не трогаем.
                var exists = false
                db.query("SELECT `name` FROM `categories`").use { c ->
                    while (c.moveToNext()) if (sameName(c.getString(0), DefaultCategories.UNACCOUNTED)) exists = true
                }
                if (!exists) {
                    db.execSQL(
                        "INSERT INTO `categories` (`name`, `emoji`, `color`, `sortOrder`, `isArchived`) " +
                            "SELECT '${DefaultCategories.UNACCOUNTED}', '${DefaultCategories.UNACCOUNTED_EMOJI}', NULL, " +
                            "COALESCE((SELECT MAX(`sortOrder`) FROM `categories` WHERE `name` <> '${DefaultCategories.OTHER}'), -1) + 1, 0"
                    )
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amountKopecks` INTEGER NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `subcategoryId` INTEGER,
                        `place` TEXT,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_templates_categoryId` ON `templates` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_templates_subcategoryId` ON `templates` (`subcategoryId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `subcategoryId` INTEGER,
                        `hits` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_categoryId` ON `rules` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_subcategoryId` ON `rules` (`subcategoryId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rules_pattern` ON `rules` (`pattern`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_phrases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `resultJson` TEXT, `error` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_phrases_status` ON `pending_phrases` (`status`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amountKopecks` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `subcategoryId` INTEGER,
                        `place` TEXT,
                        `periodUnit` TEXT NOT NULL,
                        `periodEvery` INTEGER NOT NULL,
                        `nextEpochDay` INTEGER NOT NULL,
                        `remindDaysBefore` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `autoDetected` INTEGER NOT NULL,
                        `detectKey` TEXT,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_subcategoryId` ON `recurring` (`subcategoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_detectKey` ON `recurring` (`detectKey`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `amountVaries` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `isImpulsive` INTEGER NOT NULL DEFAULT 0")
                // До этой версии ⚡ работала по названиям: переносим их во флажок
                val names = DefaultCategories.IMPULSIVE.joinToString(",") { "'$it'" }
                db.execSQL("UPDATE `categories` SET `isImpulsive` = 1 WHERE `name` IN ($names)")
            }
        }

}
