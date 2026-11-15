package ru.my.traty.data.debt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.entryTimestamp
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DebtPaymentRemovalTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: DebtRepository
    private lateinit var removal: DebtPaymentRemoval
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.now(zone)
    private var techId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CategoryRepository(db).seedIfEmpty()
        techId = db.categories().getAllWithSubs().single { it.category.name == "Дом и быт" }.category.id
        repo = DebtRepository(db)
        removal = DebtPaymentRemoval(db)
    }

    @After
    fun tearDown() = db.close()

    /** «аванс за телевизор 25 000» голосом, потом «Да» в вопросе «Покупка частями?». */
    private suspend fun voiceAdvance(): Pair<Long, Expense> {
        val e = Expense(
            amountKopecks = 2_500_000, categoryId = techId, place = "Ozon", note = "телевизор",
            timestamp = entryTimestamp(today, zone, today), source = ExpenseSource.VOICE,
        )
        val expenseId = db.expenses().insert(e)
        val debtId = repo.startFromExpense(
            Debt(title = "Телевизор", place = "Ozon", categoryId = techId, totalKopecks = 5_000_000), expenseId, zone, today,
        )
        return debtId to db.expenses().getByIds(listOf(expenseId)).single().expense
    }

    @Test
    fun deletingOnlyAdvanceRemovesPurchase() = runTest {
        val (debtId, advance) = voiceAdvance()
        val removed = removal.delete(advance, zone)
        assertTrue(removed.debtRemoved)
        assertNull(repo.getById(debtId))
        assertTrue(repo.getActive().isEmpty())
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun undoBringsBackPurchaseAndLink() = runTest {
        val (_, advance) = voiceAdvance()
        val removed = removal.delete(advance, zone)
        removal.restore(removed)
        val debt = repo.getActive().single()
        val restored = db.expenses().getAllWithCategory().single().expense
        assertEquals(debt.id, repo.getDebtByExpenseId(restored.id)?.id)
        assertEquals(2_500_000L, checkNotNull(repo.status(debt.id, zone, today)).paidPrincipalKopecks)
    }

    @Test
    fun deletingTopUpKeepsPurchase() = runTest {
        val (debtId, _) = voiceAdvance()
        assertTrue(repo.addPayment(debtId, 500_000, today, zone, today))
        val topUp = db.expenses().getAllWithCategory().first { it.expense.amountKopecks == 500_000L }.expense
        assertFalse(removal.delete(topUp, zone).debtRemoved)
        assertNotNull(repo.getById(debtId))
        assertEquals(2_500_000L, checkNotNull(repo.status(debtId, zone, today)).remainingKopecks)
    }

    @Test
    fun loanTakenWithoutPaymentIsNotAnOrphan() = runTest {
        repo.start(
            Debt(
                title = "Ноутбук", categoryId = techId, kind = DebtKind.LOAN.key, totalKopecks = 8_000_000,
                rateBasisPoints = 2000, paymentsCount = 12, firstDueEpochDay = today.plusMonths(1).toEpochDay(),
            ),
            zone = zone, today = today,
        )
        assertEquals(0, removal.dropOrphans(zone))
        assertEquals(1, repo.getActive().size)
    }

    @Test
    fun oldOrphansAreCleanedUp() = runTest {
        // Так оставалось до исправления: трату удалили напрямую, покупка висела без платежей
        val (_, advance) = voiceAdvance()
        db.expenses().delete(advance)
        assertEquals(1, removal.dropOrphans(zone))
        assertTrue(repo.getActive().isEmpty())
    }
}
