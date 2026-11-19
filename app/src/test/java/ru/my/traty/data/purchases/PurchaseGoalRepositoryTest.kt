package ru.my.traty.data.purchases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.reporting.FinancialReportRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PurchaseGoalRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PurchaseGoalRepository
    private val zone = ZoneId.of("Europe/Moscow")
    private val clock = Clock.fixed(Instant.parse("2026-12-03T09:00:00Z"), zone)
    private val today = LocalDate.now(clock)
    private val due = today.plusDays(29)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PurchaseGoalRepository(db, CategoryRepository(db), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun partialContributionsReduceTodayAndCanBeCorrectedInHistory() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.contribute(id, 15_000)
        repository.contribute(id, 5_000)
        val initial = repository.listActive().single()
        assertEquals(20_000L, initial.remainingToday(today))
        val first = initial.contributions.first { it.amountKopecks == 15_000L }
        repository.editContribution(first.id, 35_000)
        assertEquals(0L, repository.listActive().single().remainingToday(today))
        assertEquals(40_000L, db.expenses().getAllWithCategory().sumOf { it.expense.amountKopecks })
        repository.deleteContribution(first.id)
        assertEquals(35_000L, repository.listActive().single().remainingToday(today))
    }

    @Test
    fun finishingAtHigherPriceSpendsOnlyDifference() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.contribute(id, 1_200_000)
        repository.finish(id, 1_250_000)
        assertEquals(listOf(50_000L, 1_200_000L), db.expenses().getAllWithCategory().map { it.expense.amountKopecks }.sorted())
        assertTrue(repository.listActive().isEmpty())
    }

    @Test
    fun finishingAtLowerPriceReturnsTheDifference() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.contribute(id, 1_200_000)
        repository.finish(id, 1_150_000)
        assertEquals(1_200_000L, db.expenses().getAllWithCategory().single().expense.amountKopecks)
        assertEquals(50_000L, db.incomes().getAll().single().amountKopecks)
        assertTrue(repository.listActive().isEmpty())
    }
    @Test
    fun cancellationRefundsOnceAndPreservesContributions() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.contribute(id, 200_000)
        repository.contribute(id, 40_000)

        repository.cancel(id)
        repository.cancel(id)

        val refund = db.incomes().getAll().single()
        assertEquals(240_000L, refund.amountKopecks)
        assertEquals("Наушники", refund.note)
        assertEquals(2, db.expenses().getAllWithCategory().size)
        assertEquals(2, db.purchaseGoals().getAllContributions().size)
        assertTrue(repository.listActive().isEmpty())
    }

    @Test
    fun cancellingEmptyGoalDeletesItWithoutRefund() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.cancel(id)
        assertTrue(db.purchaseGoals().getAllGoals().isEmpty())
        assertTrue(db.incomes().getAll().isEmpty())
    }

    @Test
    fun hidingPurchasedGoalDoesNotRefundAgain() = runTest {
        val id = repository.create("Наушники", 1_200_000, due)
        repository.contribute(id, 1_200_000)
        repository.finish(id, 1_200_000)
        repository.hideCompleted(id)
        assertTrue(db.purchaseGoals().getAllGoals().single().hidden)
        assertTrue(db.incomes().getAll().isEmpty())
        assertEquals(1, db.expenses().getAllWithCategory().size)
    }

    @Test
    fun reportsExcludeCancelledMovementsAcrossPeriodsButKeepLedgerAndCompletedPurchases() = runTest {
        val cancelled = repository.create("Наушники", 90_000, due)
        repository.contribute(cancelled, 90_000)
        val completed = repository.create("Клавиатура", 60_000, due)
        repository.contribute(completed, 60_000)
        repository.finish(completed, 50_000)
        val later = clock.instant().plusSeconds(86_400)
        PurchaseGoalRepository(db, CategoryRepository(db), Clock.fixed(later, zone)).cancel(cancelled)

        val dayOne = clock.millis()
        val dayTwo = later.toEpochMilli()
        val reports = FinancialReportRepository(db)
        assertEquals(listOf(60_000L), reports.expenses(dayOne, dayTwo).first().map { it.expense.amountKopecks })
        assertEquals(listOf(10_000L), reports.incomes(dayOne, dayTwo).first().map { it.amountKopecks })
        assertTrue(reports.incomes(dayTwo, dayTwo + 1).first().isEmpty())
        assertEquals(2, db.expenses().getAllWithCategory().size)
        assertEquals(2, db.incomes().getAll().size)
    }

    @Test
    fun reportsRecognizeCancellationRefundWrittenByPreviousVersion() = runTest {
        val goal = repository.create("Наушники", 120_000, due)
        repository.contribute(goal, 75_000)
        repository.cancel(goal)
        val oldRefund = db.incomes().getAll().single().copy(origin = IncomeOrigin.PURCHASE_REFUND)
        db.incomes().update(oldRefund)

        val reports = FinancialReportRepository(db)
        assertTrue(reports.incomes(clock.millis(), clock.millis() + 1).first().isEmpty())
        assertEquals(75_000L, db.incomes().getAll().single().amountKopecks)
    }
}
