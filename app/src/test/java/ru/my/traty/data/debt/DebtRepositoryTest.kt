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
}
