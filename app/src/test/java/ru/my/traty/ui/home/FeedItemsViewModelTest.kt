package ru.my.traty.ui.home

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.items.ExpenseItem
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class FeedItemsViewModelTest {
    private lateinit var app: TratyApp

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `считает товары каждой покупки, траты без товаров в списке нет`() = runTest(timeout = 20.seconds) {
        val vm = FeedItemsViewModel(app)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.counts.collect {} }
        val catId = app.container.db.categories().insert(Category(name = "Продукты", emoji = "🛒", sortOrder = 0))
        val shop = app.container.db.expenses().insert(Expense(amountKopecks = 500_00, categoryId = catId, timestamp = 1L))
        val coffee = app.container.db.expenses().insert(Expense(amountKopecks = 250_00, categoryId = catId, timestamp = 2L))
        app.container.db.expenses().insert(Expense(amountKopecks = 100_00, categoryId = catId, timestamp = 3L))
        app.container.expenseItems.replace(
            shop,
            listOf(ExpenseItem(name = "яблоки"), ExpenseItem(name = "творог"), ExpenseItem(name = "масло")),
        )
        app.container.expenseItems.replace(coffee, listOf(ExpenseItem(name = "кофе")))

        // Room может сначала выдать обновление только первой покупки: ждём обе записи.
        val counts = vm.counts.filter { shop in it && coffee in it }.first()

        assertEquals(mapOf(shop to 3, coffee to 1), counts)
    }
}
