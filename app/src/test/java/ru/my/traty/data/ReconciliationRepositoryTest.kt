package ru.my.traty.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ReconciliationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var repo: ReconciliationRepository
    private val zone: ZoneId = ZoneOffset.UTC

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private val t0 = millis(2026, 9, 1, 10)
    private val t1 = millis(2026, 10, 1, 10)
    private val t2 = millis(2026, 11, 1, 10)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categories = CategoryRepository(db)
        categories.seedIfEmpty()
        repo = ReconciliationRepository(db, categories)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun otherId(): Long =
        db.categories().getAllWithSubs().first { it.category.name == DefaultCategories.OTHER }.category.id

    private suspend fun addExpense(kopecks: Long, ts: Long) =
        db.expenses().insertAll(listOf(Expense(amountKopecks = kopecks, categoryId = otherId(), timestamp = ts)))

    private suspend fun addIncome(kopecks: Long, ts: Long) {
        db.incomes().insert(Income(amountKopecks = kopecks, timestamp = ts))
    }

    private suspend fun allExpenses() = db.expenses().getAllWithCategory()

    /** Капитал 20 000, затем пополнение 80 000 и трата 71 500 -> ожидаемое 28 500. */
    private suspend fun openedWithMovements() {
        repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)
        addIncome(80_000_00, t0 + 1_000)
        addExpense(71_500_00, t0 + 2_000)
    }

    private suspend fun assertReconcileError(block: suspend () -> Unit) {
        try {
            block()
            fail("Ожидалась ReconcileException")
        } catch (_: ReconcileException) {
        }
    }

    @Test
    fun firstReconciliationOnlyStoresCapital() = runTest {
        addExpense(500_00, t0 - 10_000)
        assertNull(repo.expectation())

        val outcome = repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)

        assertEquals(ReconcileOutcome.Opening(20_000_00), outcome)
        assertEquals(1, allExpenses().size)
        assertTrue(db.incomes().getAll().isEmpty())
        val last = repo.last()!!
        assertTrue(last.isOpening)
        assertNull(last.expectedKopecks)
        assertEquals(0L, last.differenceKopecks)
        assertNotNull(repo.expectation())
    }

    @Test
    fun zeroActualIsValidCapital() = runTest {
        assertEquals(ReconcileOutcome.Opening(0), repo.reconcile(0, t0, LocalDate.of(2026, 9, 1), zone))
    }

    @Test
    fun negativeActualIsRejected() = runTest {
        assertReconcileError { repo.reconcile(-1, t0, LocalDate.of(2026, 9, 1), zone) }
        assertNull(repo.last())
    }

    @Test
    fun entryNeverLaterThanAnchor() = runTest {
        repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)
        val today = LocalDate.of(2026, 10, 1)
        val nineAm = millis(2026, 10, 1, 9)

        val outcome = repo.reconcile(19_000_00, nineAm, today, zone)

        assertEquals(ReconcileOutcome.Unaccounted(1_000_00), outcome)
        val entry = allExpenses().single().expense
        assertEquals(nineAm, entry.timestamp)
        val e = repo.expectation()!!
        assertEquals(0L, e.incomesKopecks)
        assertEquals(0L, e.expensesKopecks)
    }

    @Test
    fun unaccountedExpenseWhenLess() = runTest {
        openedWithMovements()
        assertEquals(28_500_00L, repo.expectation()!!.expectedKopecks)

        val outcome = repo.reconcile(25_500_00, t1, LocalDate.of(2026, 9, 20), zone)

        assertEquals(ReconcileOutcome.Unaccounted(3_000_00), outcome)
        val entry = allExpenses().single { it.expense.source == ExpenseSource.RECONCILE }
        assertEquals(DefaultCategories.UNACCOUNTED, entry.categoryName)
        assertEquals(3_000_00L, entry.expense.amountKopecks)
        assertEquals(millis(2026, 9, 20, 12), entry.expense.timestamp)
        val rec = repo.last()!!
        assertEquals(28_500_00L, rec.expectedKopecks)
        assertEquals(-3_000_00L, rec.differenceKopecks)
        assertEquals(entry.expense.id, rec.expenseId)
        assertNull(rec.incomeId)
        assertEquals(false, rec.isOpening)
    }

    @Test
    fun surplusIncomeWhenMore() = runTest {
        openedWithMovements()

        val outcome = repo.reconcile(30_000_00, t1, LocalDate.of(2026, 9, 20), zone)

        assertEquals(ReconcileOutcome.Surplus(1_500_00), outcome)
        val inc = db.incomes().getAll().single { it.origin == IncomeOrigin.RECONCILE }
        assertEquals(DefaultCategories.OTHER, inc.source)
        assertEquals(1_500_00L, inc.amountKopecks)
        assertEquals(millis(2026, 9, 20, 12), inc.timestamp)
        val rec = repo.last()!!
        assertEquals(1_500_00L, rec.differenceKopecks)
        assertEquals(inc.id, rec.incomeId)
        assertNull(rec.expenseId)
    }

    @Test
    fun matchedWritesNoEntries() = runTest {
        openedWithMovements()
        val expensesBefore = allExpenses().size
        val incomesBefore = db.incomes().getAll().size

        val outcome = repo.reconcile(28_500_00, t1, LocalDate.of(2026, 9, 20), zone)

        assertEquals(ReconcileOutcome.Matched, outcome)
        assertEquals(expensesBefore, allExpenses().size)
        assertEquals(incomesBefore, db.incomes().getAll().size)
        val rec = repo.last()!!
        assertEquals(0L, rec.differenceKopecks)
        assertNull(rec.expenseId)
        assertNull(rec.incomeId)
        assertEquals(false, rec.isOpening)
        assertEquals(28_500_00L, rec.expectedKopecks)
    }

    @Test
    fun discrepancyEntryDoesNotAffectNextExpectation() = runTest {
        openedWithMovements()
        repo.reconcile(25_500_00, t1, LocalDate.of(2026, 9, 20), zone)

        val e = repo.expectation()!!

        assertEquals(25_500_00L, e.capitalKopecks)
        assertEquals(t1, e.sinceMillis)
        assertEquals(0L, e.incomesKopecks)
        assertEquals(0L, e.expensesKopecks)
    }

    @Test
    fun expenseDatedBeforeAnchorButRecordedLaterIsIgnored() = runTest {
        repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)
        repo.reconcile(20_000_00, t1, LocalDate.of(2026, 10, 1), zone)
        addExpense(700_00, t1 - 60_000) // добавлена позже, но датирована раньше якоря
        addExpense(100_00, t1) // ровно на якоре тоже не входит (строго >)
        addExpense(300_00, t1 + 1)

        val e = repo.expectation()!!

        assertEquals(300_00L, e.expensesKopecks)
        assertEquals(19_700_00L, e.expectedKopecks)
    }

    @Test
    fun secondCallNotAfterAnchorIsRejected() = runTest {
        repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)

        assertReconcileError { repo.reconcile(10_000_00, t0, LocalDate.of(2026, 9, 1), zone) }

        assertEquals(1, db.reconciliations().getAll().size)
        assertEquals(20_000_00L, repo.last()!!.actualKopecks)
    }

    @Test
    fun redoReplacesLastAndItsEntry() = runTest {
        openedWithMovements()
        repo.reconcile(25_500_00, t1, LocalDate.of(2026, 9, 20), zone)

        val outcome = repo.redoLast(26_000_00, t2, zone)

        assertEquals(ReconcileOutcome.Unaccounted(2_500_00), outcome)
        assertEquals(2, db.reconciliations().getAll().size)
        val entries = allExpenses().filter { it.expense.source == ExpenseSource.RECONCILE }
        assertEquals(1, entries.size)
        assertEquals(2_500_00L, entries.single().expense.amountKopecks)
        assertEquals(millis(2026, 9, 20, 12), entries.single().expense.timestamp)
        assertEquals(t2, repo.last()!!.timestamp)
        assertEquals(28_500_00L, repo.last()!!.expectedKopecks)
    }

    @Test
    fun redoOfOpeningReplacesCapital() = runTest {
        repo.reconcile(20_000_00, t0, LocalDate.of(2026, 9, 1), zone)

        val outcome = repo.redoLast(21_000_00, t1, zone)

        assertEquals(ReconcileOutcome.Opening(21_000_00), outcome)
        assertEquals(1, db.reconciliations().getAll().size)
        assertEquals(21_000_00L, repo.last()!!.actualKopecks)
    }

    @Test
    fun skippedPeriodsAreSummedIntoOneEntry() = runTest {
        repo.reconcile(50_000_00, t0, LocalDate.of(2026, 9, 1), zone)
        addExpense(10_000_00, millis(2026, 9, 10))
        addExpense(8_000_00, millis(2026, 10, 10))
        addIncome(5_000_00, millis(2026, 10, 15))
        addExpense(2_000_00, millis(2026, 11, 5))
        // ожидаемое: 50 000 + 5 000 - 20 000 = 35 000

        val outcome = repo.reconcile(30_000_00, millis(2026, 12, 1, 10), LocalDate.of(2026, 12, 1), zone)

        assertEquals(ReconcileOutcome.Unaccounted(5_000_00), outcome)
        assertEquals(1, allExpenses().count { it.expense.source == ExpenseSource.RECONCILE })
        assertEquals(2, db.reconciliations().getAll().size)
    }
}
