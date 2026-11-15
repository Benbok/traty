package ru.my.traty.data.debt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.ExpenseSource
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DebtRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: DebtRepository
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 15)
    private var techId = 0L
    private var debtsCategoryId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CategoryRepository(db).seedIfEmpty()
        val cats = db.categories().getAllWithSubs()
        techId = cats.single { it.category.name == "Дом и быт" }.category.id
        debtsCategoryId = cats.single { it.category.name == DebtRepository.INTEREST_CATEGORY }.category.id
        repo = DebtRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun advanceThenTopUpAreExpensesLinkedToPurchase() = runTest {
        val id = repo.start(
            Debt(title = "холодильник", place = "Ozon", categoryId = techId, totalKopecks = 6_000_000),
            firstPaymentKopecks = 2_000_000, day = today.minusDays(1), zone = zone, today = today,
        )
        assertTrue(repo.addPayment(id, 500_000, today.plusDays(7), zone, today.plusDays(7)))

        val expenses = db.expenses().getAllWithCategory().sortedBy { it.expense.timestamp }
        assertEquals(listOf(2_000_000L, 500_000L), expenses.map { it.expense.amountKopecks })
        assertTrue(expenses.all { it.expense.source == ExpenseSource.DEBT && it.expense.categoryId == techId })
        assertEquals(listOf("Холодильник · аванс", "Холодильник · доплата"), expenses.map { it.expense.note })

        val status = checkNotNull(repo.status(id, zone, today.plusDays(7)))
        assertEquals(3_500_000L, status.remainingKopecks)
    }

    @Test
    fun loanPaymentSplitsInterestIntoDebtsCategory() = runTest {
        val id = repo.start(
            Debt(
                title = "Ноутбук", categoryId = techId, kind = DebtKind.LOAN.key, totalKopecks = 8_000_000,
                rateBasisPoints = 2000, paymentsCount = 12, firstDueEpochDay = today.toEpochDay(),
            ),
            zone = zone, today = today,
        )
        assertTrue(repo.addPayment(id, 741_076, today, zone, today))
        val byCategory = db.expenses().getAllWithCategory().associate { it.expense.categoryId to it.expense.amountKopecks }
        assertEquals(607_743L, byCategory[techId])
        assertEquals(133_333L, byCategory[debtsCategoryId])
    }

    @Test
    fun lastPaymentClosesPurchaseAndDeletedExpenseReopensRemainder() = runTest {
        val id = repo.start(Debt(title = "Диван", categoryId = techId, totalKopecks = 1_000_000), 600_000, today, zone, today)
        assertTrue(repo.addPayment(id, 400_000, today, zone, today))
        assertTrue(repo.getActive().isEmpty())
        assertFalse(repo.addPayment(id, 100, today, zone, today))

        // Удалили трату — связь уходит каскадом, остаток снова виден
        val last = db.expenses().getAllWithCategory().first { it.expense.amountKopecks == 400_000L }.expense
        db.expenses().delete(last)
        assertEquals(400_000L, checkNotNull(repo.status(id, zone, today)).remainingKopecks)
    }

    @Test
    fun savedVoiceExpensesBecomePurchaseAndTopUp() = runTest {
        val advance = db.expenses().insert(
            ru.my.traty.data.Expense(amountKopecks = 2_000_000, categoryId = techId, place = "Ozon", note = "холодильник", timestamp = 1000, source = ExpenseSource.VOICE)
        )
        val id = repo.startFromExpense(
            Debt(title = "холодильник", place = "Ozon", categoryId = techId, totalKopecks = 6_000_000), advance, zone, today,
        )
        val topUp = db.expenses().insert(
            ru.my.traty.data.Expense(amountKopecks = 500_000, categoryId = techId, place = "Ozon", note = "холодильник", timestamp = 2000, source = ExpenseSource.VOICE)
        )
        assertTrue(repo.attachExpense(id, topUp, zone, today))
        assertFalse(repo.attachExpense(id, topUp, zone, today))

        val saved = db.expenses().getAllWithCategory().map { it.expense }.sortedBy { it.timestamp }
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.source == ExpenseSource.VOICE })
        assertEquals(listOf("Холодильник · аванс", "Холодильник · доплата"), saved.map { it.note })
        assertEquals(3_500_000L, checkNotNull(repo.status(id, zone, today)).remainingKopecks)
    }

    @Test
    fun unpaidLoanStartsWithoutExpense() = runTest {
        val id = repo.startFromExpense(
            Debt(
                title = "Ноутбук", categoryId = techId, kind = DebtKind.LOAN.key, totalKopecks = 8_000_000,
                rateBasisPoints = 2000, paymentsCount = 12, firstDueEpochDay = today.plusMonths(1).toEpochDay(),
            ),
            expenseId = null, zone = zone, today = today,
        )
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
        val next = checkNotNull(checkNotNull(repo.status(id, zone, today)).next)
        assertEquals(today.plusMonths(1), next.date)
        assertEquals(741_076L, next.totalKopecks)
    }

    @Test
    fun deleteDebtRemovesAutoDebtExpensesButKeepsVoiceExpenses() = runTest {
        // Автосозданный платёж (source=DEBT) — должен удалиться вместе с долгом
        val autoDebtId = repo.start(
            Debt(title = "Диван", categoryId = techId, totalKopecks = 1_000_000),
            firstPaymentKopecks = 300_000, day = today, zone = zone, today = today,
        )
        // Привязанная голосовая трата (source=VOICE) — должна остаться в истории
        val voiceExpenseId = db.expenses().insert(
            ru.my.traty.data.Expense(amountKopecks = 2_000_000, categoryId = techId, place = "Ozon", timestamp = 1000, source = ExpenseSource.VOICE)
        )
        val voiceDebtId = repo.startFromExpense(
            Debt(title = "Холодильник", place = "Ozon", categoryId = techId, totalKopecks = 6_000_000),
            voiceExpenseId, zone, today,
        )

        // После удаления обоих долгов: только голосовая трата остаётся в базе
        repo.delete(autoDebtId)
        repo.delete(voiceDebtId)

        val remaining = db.expenses().getAllWithCategory()
        assertEquals(1, remaining.size)
        assertEquals(voiceExpenseId, remaining.single().expense.id)
        assertEquals(ExpenseSource.VOICE, remaining.single().expense.source)
    }

    @Test
    fun deletionImpactCountsAppPaymentsAndKeptExpenses() = runTest {
        val id = repo.start(Debt(title = "Диван", categoryId = techId, totalKopecks = 5_000_000), 1_000_000, today, zone, today)
        assertTrue(repo.addPayment(id, 500_000, today, zone, today))
        // Трата, которую пользователь записал сам и привязал к покупке: при удалении остаётся
        val own = db.expenses().insert(
            ru.my.traty.data.Expense(amountKopecks = 200_000, categoryId = techId, timestamp = System.currentTimeMillis(), source = ExpenseSource.VOICE)
        )
        assertTrue(repo.attachExpense(id, own, zone, today))

        val impact = repo.deletionImpact(id)
        assertEquals(2, impact.removedCount)
        assertEquals(1_500_000L, impact.removedKopecks)
        assertEquals(1, impact.keptCount)

        repo.delete(id)
        assertEquals(listOf(200_000L), db.expenses().getAllWithCategory().map { it.expense.amountKopecks })
    }
}
