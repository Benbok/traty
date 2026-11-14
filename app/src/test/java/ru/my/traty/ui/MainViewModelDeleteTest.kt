package ru.my.traty.ui

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.entryTimestamp
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class MainViewModelDeleteTest {
    private lateinit var app: TratyApp
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
        vm = MainViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDeleteAndUndoExpense() = runTest {
        val catId = app.container.db.categories().insert(
            Category(name = "Еда", emoji = "🍔")
        )
        val expense = Expense(
            amountKopecks = 35000,
            categoryId = catId,
            timestamp = entryTimestamp(LocalDate.now(), ZoneId.systemDefault()),
            source = ExpenseSource.MANUAL,
            note = "Кофе",
        )
        val id = app.container.db.expenses().insert(expense)
        val inserted = expense.copy(id = id)

        // Ensure item exists
        var expensesList = app.container.db.expenses().observeAll().first()
        assertTrue(expensesList.any { it.id == id })

        // Delete via ViewModel
        vm.delete(inserted).join()

        // Check it was removed and undo state is populated
        expensesList = app.container.db.expenses().observeAll().first()
        assertTrue(expensesList.none { it.id == id })
        assertEquals(inserted, vm.deletedForUndo)
        assertEquals("Трата удалена", vm.message)

        // Perform undo
        vm.undoDelete()

        // Check it was restored
        expensesList = app.container.db.expenses().observeAll().first()
        assertTrue(expensesList.any { it.id == id })
        assertNull(vm.deletedForUndo)
    }
}
