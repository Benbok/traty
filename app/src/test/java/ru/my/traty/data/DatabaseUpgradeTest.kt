package ru.my.traty.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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

    /** База в формате v3 — SQL взят дословно из app/schemas/.../3.json. */
    private fun createV3Database(extraCategory: String? = null) {
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v3 ->
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER, `sortOrder` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)"
            )
            v3.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `subcategories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`categoryId` INTEGER NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategories_categoryId` ON `subcategories` (`categoryId`)")
            v3.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_subcategories_categoryId_name` ON `subcategories` (`categoryId`, `name`)"
            )
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`amountKopecks` INTEGER NOT NULL, `currency` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                    "`subcategoryId` INTEGER, `place` TEXT, `note` TEXT, `timestamp` INTEGER NOT NULL, `rawText` TEXT, `source` TEXT NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_subcategoryId` ON `expenses` (`subcategoryId`)")
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
            v3.execSQL(
                "CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`amountKopecks` INTEGER NOT NULL, `currency` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT, `timestamp` INTEGER NOT NULL)"
            )
            v3.execSQL("CREATE INDEX IF NOT EXISTS `index_incomes_timestamp` ON `incomes` (`timestamp`)")
            v3.execSQL("INSERT INTO categories (id, name, emoji, color, sortOrder, isArchived) VALUES (1, 'Продукты', '🛒', NULL, 0, 0)")
            extraCategory?.let {
                v3.execSQL("INSERT INTO categories (id, name, emoji, color, sortOrder, isArchived) VALUES (2, '$it', '❓', NULL, 1, 0)")
            }
            v3.execSQL(
                "INSERT INTO expenses (id, amountKopecks, currency, categoryId, subcategoryId, place, note, timestamp, rawText, source) " +
                    "VALUES (1, 35000, 'RUB', 1, NULL, 'Магнит', 'Молоко', 1000, NULL, 'manual')"
            )
            v3.execSQL(
                "INSERT INTO incomes (id, amountKopecks, currency, source, note, timestamp) " +
                    "VALUES (1, 5000000, 'RUB', 'Зарплата', NULL, 2000)"
            )
            v3.version = 3
        }
    }

    @Test
    fun v3DatabaseMigratesToV4WithoutDataLoss() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val expenses = db.expenses().getAllWithCategory()
            assertEquals(1, expenses.size)
            assertEquals(35000L, expenses[0].expense.amountKopecks)
            val incomes = db.incomes().getAll()
            assertEquals(1, incomes.size)
            assertEquals(5000000L, incomes[0].amountKopecks)
            assertEquals("manual", incomes[0].origin)

            val unaccounted = db.categories().getAllWithSubs().single { it.category.name == DefaultCategories.UNACCOUNTED }
            assertEquals(DefaultCategories.UNACCOUNTED_EMOJI, unaccounted.category.emoji)
            assertTrue(!unaccounted.category.isArchived)

            assertTrue(db.reconciliations().getAll().isEmpty())
            val id = db.reconciliations().insert(
                Reconciliation(
                    timestamp = 3000, actualKopecks = 100, expectedKopecks = null,
                    differenceKopecks = 100, expenseId = null, incomeId = null, isOpening = true,
                )
            )
            assertEquals(listOf(id), db.reconciliations().getAll().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV7WithPendingPhrasesAndKeepsData() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            assertEquals(1, db.expenses().getAllWithCategory().size)
            assertEquals(1, db.incomes().getAll().size)
            val id = db.pendingPhrases().insert(PendingPhrase(text = "кофе 250", createdAt = 5000))
            val waiting = db.pendingPhrases().getByStatus(PendingStatus.WAITING)
            assertEquals(listOf(id), waiting.map { it.id })
            assertEquals(5000L, waiting.single().createdAt)
            // sumRange: трата 350 ₽ в диапазоне [0, 2000), вне диапазона не учитывается
            assertEquals(35000L, db.expenses().sumRange(0, 2000))
            assertEquals(0L, db.expenses().sumRange(2000, 3000))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV10AndFlagsImpulsiveCategoriesByName() = runTest {
        createV3Database(extraCategory = "Кафе и рестораны")
        val db = AppDatabase.build(context, dbName)
        try {
            val all = db.categories().getAllWithSubs().map { it.category }
            // Категория, импульсивная по названию, получает флажок при переходе на v10.
            val cafe = all.single { it.name == "Кафе и рестораны" }
            assertEquals(true, cafe.isImpulsive)
            assertEquals(false, all.single { it.name == "Продукты" }.isImpulsive)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV11WithBudgetsAndRepositoryWorks() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val categoryId = db.categories().getAllWithSubs().first().category.id
            val repo = ru.my.traty.data.budget.BudgetRepository(db)
            val key = ru.my.traty.data.budget.PlanKey(categoryId)
            repo.set(key, 202611, 5_000_00, repeats = true)
            repo.set(key, 202611, 6_000_00, repeats = true)
            assertEquals(1, db.budgets().getAll().size)
            assertEquals(6_000_00L, db.budgets().getAll().single().amountKopecks)
            repo.removeFrom(key, 202612)
            val rows = db.budgets().getAll()
            assertEquals(1, ru.my.traty.data.budget.BudgetPlans.effective(rows, 202611).size)
            assertTrue(ru.my.traty.data.budget.BudgetPlans.effective(rows, 202612).isEmpty())
            // Данные прежних версий на месте.
            assertEquals(1, db.expenses().getAllWithCategory().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV12WithMonthLimit() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val repo = ru.my.traty.data.budget.BudgetRepository(db)
            repo.setTotal(202611, 80_000_00, repeats = true)
            repo.setTotal(202611, 90_000_00, repeats = true)
            assertEquals(1, db.monthBudgets().getAll().size)
            repo.removeTotalFrom(202612)
            val rows = db.monthBudgets().getAll()
            assertEquals(90_000_00L, ru.my.traty.data.budget.TotalBudgets.effective(rows, 202611)?.amountKopecks)
            assertEquals(null, ru.my.traty.data.budget.TotalBudgets.effective(rows, 202701))
            assertEquals(1, db.expenses().getAllWithCategory().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV13WithWeeklyTasks() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val repo = ru.my.traty.data.weekly.WeeklyTaskRepository(db)
            // Истории мало (одна трата) — заданий не предлагается, но и ошибки нет
            assertTrue(repo.ensureWeek(java.time.LocalDate.of(2026, 11, 18)).isEmpty())
            val mon = java.time.LocalDate.of(2026, 11, 16)
            val custom = ru.my.traty.data.weekly.WeeklyTaskTexts.customTask(
                mon, ru.my.traty.data.weekly.WeeklyTaskType.COUNT_LIMIT, 1, "Продукты", 2,
            )
            repo.add(custom)
            val week = repo.ensureWeek(mon.plusDays(2))
            assertEquals(listOf("«Продукты» — не больше 2 раз"), week.map { it.title })
            repo.remove(week.single())
            assertTrue(repo.ensureWeek(mon.plusDays(2)).isEmpty())
            assertEquals(1, db.expenses().getAllWithCategory().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV14WithPurchasesInParts() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val categoryId = db.expenses().getAllWithCategory().single().expense.categoryId
            val repo = ru.my.traty.data.debt.DebtRepository(db)
            val id = repo.start(
                ru.my.traty.data.debt.Debt(title = "Холодильник", place = "Ozon", categoryId = categoryId, totalKopecks = 6_000_000),
                firstPaymentKopecks = 2_000_000,
            )
            assertEquals(4_000_000L, repo.status(id)?.remainingKopecks)
            // Старая трата на месте, платёж добавился второй тратой
            assertEquals(2, db.expenses().getAllWithCategory().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV15WithExpenseItems() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val old = db.expenses().getAllWithCategory().single().expense
            val repo = ru.my.traty.data.items.ExpenseItemRepository(db)
            // Старая трата без позиций, позиции добавляются к ней
            assertTrue(repo.getFor(old.id).isEmpty())
            repo.replace(old.id, listOf(ru.my.traty.data.items.ExpenseItem(name = "Молоко", priceKopecks = 9_000)))
            assertEquals(9_000L, repo.getFor(old.id).single().priceKopecks)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationChainReachesV9WithRecurringTable() = runTest {
        createV3Database()
        val db = AppDatabase.build(context, dbName)
        try {
            val categoryId = db.categories().getAllWithSubs().first().category.id
            assertTrue(db.recurring().getAll().isEmpty())
            val id = db.recurring().insert(
                Recurring(
                    title = "Яндекс Плюс", amountKopecks = 39_900, categoryId = categoryId,
                    periodUnit = "month", nextEpochDay = 20_000, detectKey = "$categoryId|яндекс плюс",
                )
            )
            val saved = db.recurring().getById(id)!!
            assertEquals(RecurringState.ACTIVE, saved.state)
            assertEquals(1, saved.periodEvery)
            assertEquals(false, saved.amountVaries)
            assertEquals(39_900L, saved.amountKopecks)
            // Отклонённое предложение хранится, но в списке не показывается.
            db.recurring().insert(
                Recurring(
                    title = "Кофе", amountKopecks = 25_000, categoryId = categoryId, periodUnit = "week",
                    nextEpochDay = 20_007, state = RecurringState.DISMISSED, autoDetected = true,
                )
            )
            assertEquals(2, db.recurring().getAll().size)
            assertEquals(listOf("Яндекс Плюс"), db.recurring().observeVisible().first().map { it.recurring.title })
            // Данные прежних версий на месте.
            assertEquals(1, db.expenses().getAllWithCategory().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v3WithCaseVariantUnaccountedDoesNotGetDuplicate() = runTest {
        createV3Database(extraCategory = "неучтённые")
        val db = AppDatabase.build(context, dbName)
        try {
            val matches = db.categories().getAllWithSubs().filter { sameName(it.category.name, DefaultCategories.UNACCOUNTED) }
            assertEquals(1, matches.size)
            assertEquals(2L, matches[0].category.id)
            assertEquals(2L, CategoryRepository(db).ensureUnaccounted())
        } finally {
            db.close()
        }
    }

    @Test
    fun v1DatabaseIsRecreatedAndSeeded() = runTest {
        createV1Database()
        val db = AppDatabase.build(context, dbName)
        try {
            CategoryRepository(db).seedIfEmpty()
            assertTrue(db.expenses().getAllWithCategory().isEmpty())
            assertEquals(26, db.categories().getActiveWithSubs().size)
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
