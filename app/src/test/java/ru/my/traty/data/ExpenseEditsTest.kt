package ru.my.traty.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.debt.DebtPaymentRemoval
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ExpenseEditsTest {
    private lateinit var db: AppDatabase
    private lateinit var edits: ExpenseEdits
    private val zone = ZoneId.of("Europe/Moscow")
    private var food = 0L
    private var cafe = 0L
    private var coffee = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val categories = CategoryRepository(db)
        categories.seedIfEmpty()
        val all = db.categories().getAllWithSubs()
        food = all.first { it.category.name == "Продукты" }.category.id
        cafe = all.first { it.category.name == "Кафе и рестораны" }.category.id
        coffee = categories.addSubcategory(cafe, "Кофе")
        edits = ExpenseEdits(db, DebtPaymentRemoval(db))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(category: Long = food, source: String = ExpenseSource.VOICE, sub: Long? = null): Long =
        db.expenses().insert(
            Expense(
                amountKopecks = 50_000, categoryId = category, subcategoryId = sub, place = "Пятёрочка",
                timestamp = entryTimestamp(LocalDate.now(zone), zone, LocalDate.now(zone)), source = source,
            ),
        )

    private suspend fun load(id: Long) = db.expenses().getByIds(listOf(id)).firstOrNull()?.expense

    @Test
    fun deleteThenUndoBringsExpensesBack() = runTest {
        val a = add()
        val b = add()
        val result = edits.delete(listOf(a, b))
        assertEquals(2, result.done)
        assertNull(load(a))
        assertNull(load(b))
        edits.undo(result)
        assertEquals(2, db.expenses().getAllWithCategory().size)
    }

    @Test
    fun reconcileRowsAreNeverTouched() = runTest {
        val reconcile = add(source = ExpenseSource.RECONCILE)
        val result = edits.delete(listOf(reconcile))
        assertEquals(0, result.done)
        assertNotNull(load(reconcile))
        val moved = edits.move(listOf(ExpenseMove(reconcile, cafe, null, food, null)))
        assertEquals(0, moved.done)
        assertEquals(food, load(reconcile)?.categoryId)
    }

    @Test
    fun moveChangesCategoryAndUndoRestoresIt() = runTest {
        val a = add(sub = null)
        val result = edits.move(listOf(ExpenseMove(a, cafe, coffee, food, null)))
        assertEquals(1, result.done)
        assertEquals(cafe, load(a)?.categoryId)
        assertEquals(coffee, load(a)?.subcategoryId)
        edits.undo(result)
        assertEquals(food, load(a)?.categoryId)
        assertNull(load(a)?.subcategoryId)
    }

    @Test
    fun missingExpensesAreSkipped() = runTest {
        val a = add()
        val result = edits.delete(listOf(a, 9_999))
        assertEquals(1, result.done)
    }
}
