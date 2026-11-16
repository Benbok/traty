package ru.my.traty.ui.home

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.capture.DraftItem
import ru.my.traty.data.Expense
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ExpenseItemsViewModelTest {
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
    fun savedItemsComeBackAsDraft() = runTest(timeout = 20.seconds) {
        app.container.categories.seedIfEmpty()
        val catId = app.container.db.categories().getAllWithSubs().first().category.id
        val id = app.container.db.expenses().insert(Expense(amountKopecks = 4_200_000, categoryId = catId, timestamp = 1_000))
        val vm = ExpenseItemsViewModel(app)
        vm.save(id, listOf(DraftItem("телефон", priceText = "40000"), DraftItem("чехол", priceText = "2000", qtyText = "1", unit = "шт"))).join()
        val back = vm.observe(id).first { it.isNotEmpty() }
        assertEquals(listOf("Телефон", "Чехол"), back.map { it.name })
        assertEquals(listOf(4_000_000L, 200_000L), back.map { it.priceKopecks() })
        assertEquals("шт", back[1].unit)
        assertNull(vm.error)
        // Убрали все товары — позиций нет
        vm.save(id, emptyList()).join()
        assertEquals(0, app.container.expenseItems.getFor(id).size)
    }
}
