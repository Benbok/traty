package ru.my.traty.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.ExpenseSource

@RunWith(RobolectricTestRunner::class)
class CsvImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var importer: CsvImporter

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = CategoryRepository(db)
        repo.seedIfEmpty()
        importer = CsvImporter(db, repo)
    }

    @After
    fun tearDown() = db.close()

    private fun row(ts: Long, amount: Long = 25000, category: String = "Продукты", sub: String? = null) =
        CsvRow(ts, amount, "RUB", category, sub, null, null, null)

    private val day = 86_400_000L
    private val t0 = 1_700_000_000_000L

    @Test
    fun importsRowsCreatingMissingCategories() = runTest {
        val r = importer.import(CsvParseResult(listOf(row(t0), row(t0 + 1000, category = "Яхты", sub = "Парус")), emptyList()))
        assertEquals(2, r.imported)
        val yachts = db.categories().getAllWithSubs().single { it.category.name == "Яхты" }
        assertEquals(DefaultCategories.NEW_EMOJI, yachts.category.emoji)
        assertEquals(listOf("Парус"), yachts.subNames())
        assertTrue(db.expenses().getAllWithCategory().all { it.expense.source == ExpenseSource.IMPORT })
    }

    @Test
    fun reimportSkipsDuplicates() = runTest {
        val parsed = CsvParseResult(listOf(row(t0), row(t0 + day, amount = 100)), emptyList())
        importer.import(parsed)
        val second = importer.import(parsed)
        assertEquals(0, second.imported)
        assertEquals(2, second.duplicates)
        assertEquals(2, db.expenses().getAllWithCategory().size)
    }

    @Test
    fun duplicateDetectionToleratesLostSeconds() = runTest {
        importer.import(CsvParseResult(listOf(row(t0 + 42_000)), emptyList())) // 14:03:42
        val r = importer.import(CsvParseResult(listOf(row(t0)), emptyList())) // 14:03 — Excel потерял секунды
        assertEquals(1, r.duplicates)
        assertEquals(0, r.imported)
    }

    @Test
    fun sameAmountDifferentDayIsNotDuplicate() = runTest {
        importer.import(CsvParseResult(listOf(row(t0)), emptyList()))
        val r = importer.import(CsvParseResult(listOf(row(t0 + day)), emptyList()))
        assertEquals(1, r.imported)
    }

    @Test
    fun badLinesArePassedThrough() = runTest {
        val r = importer.import(CsvParseResult(emptyList(), listOf(3, 4)))
        assertEquals(listOf(3, 4), r.badLines)
    }
}
