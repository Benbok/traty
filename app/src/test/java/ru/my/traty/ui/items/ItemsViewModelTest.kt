package ru.my.traty.ui.items

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Expense
import ru.my.traty.data.entryTimestamp
import ru.my.traty.data.items.ExpenseItem
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ItemsViewModelTest {
    private lateinit var app: TratyApp
    private val day = LocalDate.of(2026, 11, 16)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun expense(place: String, kopecks: Long, vararg items: ExpenseItem) {
        val catId = app.container.db.categories().getAllWithSubs().first().category.id
        val ts = entryTimestamp(day, ZoneId.systemDefault())
        val id = app.container.db.expenses().insert(Expense(amountKopecks = kopecks, categoryId = catId, place = place, timestamp = ts))
        app.container.expenseItems.replace(id, items.toList())
    }

    @Test
    fun productsForDatesAndInsideOneStore() = runTest(timeout = 20.seconds) {
        app.container.categories.seedIfEmpty()
        expense("Эльдорадо", 4_200_000, ExpenseItem(name = "телефон", priceKopecks = 4_000_000), ExpenseItem(name = "чехол", priceKopecks = 200_000))
        expense("М.Видео", 300_000, ExpenseItem(name = "чехол", priceKopecks = 150_000), ExpenseItem(name = "кабель", priceKopecks = 150_000))
        val vm = ItemsViewModel(app)
        vm.setWindow(day, day)
        val all = vm.state.first { it.products.isNotEmpty() }
        assertEquals(350_000L, all.products.single { it.name == "Чехол" }.totalKopecks)
        assertEquals(2, all.stores.size)
        vm.selectStore("Эльдорадо")
        val inShop = vm.state.first { it.store == "Эльдорадо" }
        assertEquals(listOf("Телефон", "Чехол"), inShop.products.map { it.name })
        // Другие даты — товаров нет
        vm.setWindow(day.plusDays(1), day.plusDays(2))
        assertEquals(0, vm.state.first { it.sales.isEmpty() }.products.size)
    }
}
