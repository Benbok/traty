package ru.my.traty.data.items

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.debt.DebtPaymentRemoval

@RunWith(RobolectricTestRunner::class)
class ExpenseItemRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseItemRepository
    private var techId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CategoryRepository(db).seedIfEmpty()
        techId = db.categories().getAllWithSubs().single { it.category.name == "Дом и быт" }.category.id
        repo = ExpenseItemRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun eldorado(ts: Long = 1_000): Expense {
        val e = Expense(amountKopecks = 4_500_000, categoryId = techId, place = "Эльдорадо", timestamp = ts)
        return e.copy(id = db.expenses().insert(e))
    }

    private val items = listOf(
        ExpenseItem(name = "телефон", priceKopecks = 4_000_000),
        ExpenseItem(name = "чехол", priceKopecks = 200_000),
        ExpenseItem(name = " ", priceKopecks = 100),
        ExpenseItem(name = "клавиатура", priceKopecks = 300_000, quantityMilli = 1000, unit = "шт"),
    )

    @Test
    fun replaceKeepsOrderAndDropsEmptyNames() = runTest {
        val e = eldorado()
        repo.replace(e.id, items)
        val saved = repo.getFor(e.id)
        assertEquals(listOf("Телефон", "Чехол", "Клавиатура"), saved.map { it.name })
        assertEquals(listOf(0, 1, 2), saved.map { it.position })
        // Повторная замена не дублирует позиции
        repo.replace(e.id, saved.take(1))
        assertEquals(1, repo.getFor(e.id).size)
    }

    @Test
    fun nonPositivePriceMeansNotNamed() = runTest {
        val e = eldorado()
        repo.replace(e.id, listOf(ExpenseItem(name = "пакет", priceKopecks = 0, quantityMilli = -1)))
        val item = repo.getFor(e.id).single()
        assertNull(item.priceKopecks)
        assertNull(item.quantityMilli)
    }

    @Test
    fun deletingExpenseDeletesItemsAndUndoReturnsThem() = runTest {
        val e = eldorado()
        repo.replace(e.id, items)
        val removal = DebtPaymentRemoval(db)
        val removed = removal.delete(e)
        assertTrue(repo.getFor(e.id).isEmpty())
        removal.restore(removed)
        assertEquals(3, repo.getFor(e.id).size)
    }

    @Test
    fun salesJoinExpensePlaceAndTime() = runTest {
        val e = eldorado(ts = 5_000)
        repo.replace(e.id, items)
        val sales = repo.getSales(0, 10_000)
        assertEquals(3, sales.size)
        assertTrue(sales.all { it.place == "Эльдорадо" && it.timestamp == 5_000L })
        assertTrue(repo.getSales(6_000, 10_000).isEmpty())
        val totals = ItemTotals.byProduct(sales, place = "эльдорадо")
        assertEquals(4_500_000L, totals.sumOf { it.totalKopecks })
    }
}
