package ru.my.traty.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.DB_SCHEMA_VERSION
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.backup.BackupRepository
import ru.my.traty.data.backup.DbSnapshot
import ru.my.traty.data.portable
import ru.my.traty.data.purchases.PurchaseGoal
import ru.my.traty.data.purchases.PurchaseGoalRepository
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FullBackupRoundTripTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @Before
    fun setUp() {
        source = memoryDb()
        target = memoryDb()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun export(db: AppDatabase, settings: AppSettings = AppSettings()): FullBackup =
        FullBackupWriter.build(BackupRepository(db).snapshot(), settings.portable(), 42, "1.0", DB_SCHEMA_VERSION)

    private fun bytes(b: FullBackup) = FullBackupWriter.encode(b).toByteArray(Charsets.UTF_8)

    @Test
    fun exportThenImportIntoEmptyDatabaseGivesSameData() = runTest {
        fillAllTables(source)
        val file = export(source)
        val read = FullBackupReader.decode(bytes(file), DB_SCHEMA_VERSION)
        FullBackupImporter(BackupRepository(target), settings = null).restore(read)

        // Тот же файл из восстановленной базы: совпадает всё, включая связи по названиям и ключам
        assertEquals(file, export(target))
    }

    @Test
    fun linksSurviveWithNewIds() = runTest {
        fillAllTables(source)
        FullBackupImporter(BackupRepository(target), null).restore(export(source))

        val expenses = target.expenses().getAllWithCategory()
        val coffee = expenses.single { it.expense.rawText == "кофе 250" }
        assertEquals("Еда", coffee.categoryName)
        assertEquals("Кафе", coffee.subcategoryName)
        assertEquals("=Кофейня", coffee.expense.place)
        assertEquals("латте\n«с собой»", coffee.expense.note)

        val shop = expenses.single { it.expense.place == "Эльдорадо" }
        assertEquals(listOf("Телефон", "Чехол"), target.expenseItems().getFor(shop.expense.id).map { it.name })

        val rule = target.rules().getAll().single { it.pattern == "кофейня" }
        assertEquals(coffee.expense.categoryId, rule.categoryId)
        assertEquals(coffee.expense.subcategoryId, rule.subcategoryId)

        val fridge = target.debts().getAll().single { it.title == "Холодильник" }
        val paid = target.debts().getPayments(fridge.id)
        assertEquals(listOf(2_000_000L), paid.map { it.amountKopecks })

        // Задание удалённой категории остаётся без категории
        assertNull(target.weeklyTasks().getAll().single { it.title == "Подписки" }.categoryId)

        val fix = expenses.single { it.expense.amountKopecks == 1_234L }
        assertEquals(fix.expense.id, target.reconciliations().getAll().single { !it.isOpening }.expenseId)

        // Неразобранные фразы переносятся текстом и ждут разбора заново
        val pending = target.pendingPhrases().getByStatus(PendingStatus.WAITING)
        assertEquals(listOf("такси 400", "хлеб 60"), pending.map { it.text })
        assertTrue(pending.all { it.resultJson == null && it.error == null })
    }

    @Test
    fun purchaseGoalsSurviveExportAndRestoreWithNewIds() = runTest {
        fillAllTables(source)
        val due = LocalDate.now().plusDays(30)
        source.purchaseGoals().insertGoals(listOf(PurchaseGoal(80, "Черновик", 10_000, due.toEpochDay())))
        target.purchaseGoals().insertGoals(listOf(PurchaseGoal(70, "Старая цель", 10_000, due.toEpochDay())))
        val goals = PurchaseGoalRepository(source, CategoryRepository(source))
        val activeId = goals.create("Наушники", 1_200_000, due)
        goals.contribute(activeId, 100_000)
        val closedId = goals.create("Чехол", 50_000, due)
        goals.contribute(closedId, 50_000)
        goals.finish(closedId, 45_000)
        goals.hideCompleted(closedId)

        val file = export(source)
        FullBackupImporter(BackupRepository(target), null)
            .restore(FullBackupReader.decode(bytes(file), DB_SCHEMA_VERSION))

        val restored = target.purchaseGoals().getAllGoals()
        val active = restored.single { it.title == "Наушники" }
        val closed = restored.single { it.title == "Чехол" }
        assertTrue(restored.none { it.title == "Старая цель" })
        assertTrue(active.id != activeId)
        assertEquals(1_200_000L, active.targetKopecks)
        assertEquals(due.toEpochDay(), active.dueEpochDay)
        assertNull(active.actualPriceKopecks)
        assertEquals(45_000L, closed.actualPriceKopecks)
        assertTrue(closed.hidden)
        val amountsById = target.expenses().getAllWithCategory().associate { it.expense.id to it.expense.amountKopecks }
        val amountsByGoal = target.purchaseGoals().getAllContributions().associate { it.goalId to amountsById[it.expenseId] }
        assertEquals(mapOf(active.id to 100_000L, closed.id to 50_000L), amountsByGoal)
        assertEquals(file, export(target))
    }

    @Test
    fun restoreReplacesExistingData() = runTest {
        fillAllTables(source)
        fillAllTables(target)
        FullBackupImporter(BackupRepository(target), null).restore(export(source))
        assertEquals(export(source), export(target))
    }

    @Test
    fun failureInsideTransactionKeepsOldData() = runTest {
        fillAllTables(target)
        val before = export(target)
        val repo = BackupRepository(target)
        try {
            repo.replaceAll(FullBackupRestore.toSnapshot(export(source.also { fillAllTables(it) }))) {
                throw IllegalStateException("сбой посередине")
            }
            fail("Ожидалась ошибка")
        } catch (_: IllegalStateException) {
        }
        assertEquals(before, export(target))
    }

    @Test
    fun brokenLinkRollsBackAfterTablesWereCleared() = runTest {
        fillAllTables(target)
        val before = export(target)
        val repo = BackupRepository(target)
        val good = FullBackupRestore.toSnapshot(before)
        // Трата ссылается на категорию, которой нет: внешний ключ падает уже после очистки таблиц
        val broken = good.copy(expenses = good.expenses + good.expenses.first().copy(id = 999, categoryId = 999))
        try {
            repo.replaceAll(broken)
            fail("Ожидалась ошибка внешнего ключа")
        } catch (e: Exception) {
            assertFalse(e is FullBackupException)
        }
        assertEquals(before, export(target))
    }

    @Test
    fun garbageAndTruncatedFilesDoNotTouchDatabase() = runTest {
        fillAllTables(target)
        val before = export(target)
        val full = FullBackupWriter.encode(export(target))
        val files = listOf(
            byteArrayOf(0x50, 0x4B, 0x03, 0x04, -1, -2),
            "дата;сумма;валюта\n".toByteArray(),
            full.substring(0, full.length / 2).toByteArray(),
        )
        for (f in files) {
            try {
                FullBackupImporter(BackupRepository(target), null).restore(FullBackupReader.decode(f, DB_SCHEMA_VERSION))
                fail("Ожидалась ошибка")
            } catch (_: FullBackupException) {
            }
        }
        assertEquals(before, export(target))
    }

    @Test
    fun damagedLinksInFileAreRejectedBeforeWriting() = runTest {
        fillAllTables(target)
        val before = export(target)
        val bad = before.copy(expenses = before.expenses.map { it.copy(category = "Нет такой") })
        try {
            FullBackupImporter(BackupRepository(target), null).restore(bad)
            fail("Ожидалась ошибка")
        } catch (e: FullBackupException) {
            assertTrue(e.message.orEmpty().contains("не изменились"))
        }
        assertEquals(before, export(target))
    }

    @Test
    fun noSecretsInFile() = runTest {
        fillAllTables(source)
        val settings = AppSettings(deepseekKey = "sk-deepseek-SECRET")
        val text = FullBackupWriter.encode(export(source, settings))
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("deepseekKey"))
        assertFalse(text.contains("yandexKey"))
        assertTrue(text.contains("Ключи ИИ"))
    }

    @Test
    fun emptySnapshotIsNotWrittenOverData() {
        // Копия без категорий отклоняется ещё при чтении: иначе «заменить всё» стёрло бы данные
        val empty = FullBackupWriter.encode(
            FullBackupWriter.build(DbSnapshot(), AppSettings().portable(), 1, "", DB_SCHEMA_VERSION)
        )
        try {
            FullBackupReader.decode(empty.toByteArray(), DB_SCHEMA_VERSION)
            fail("Ожидалась ошибка")
        } catch (e: FullBackupException) {
            assertEquals(FullBackupReader.NOT_A_BACKUP, e.message)
        }
    }
}
