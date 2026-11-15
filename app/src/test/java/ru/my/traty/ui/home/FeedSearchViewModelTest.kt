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
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class FeedSearchViewModelTest {
    private lateinit var app: TratyApp
    private lateinit var vm: FeedSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
        vm = FeedSearchViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `находит трату из прошлого периода, а открытый пропускает`() = runTest(timeout = 20.seconds) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.elsewhere.collect {} }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val catId = app.container.db.categories().insert(Category(name = "Маркетплейсы", emoji = "📦", sortOrder = 0))
        val old = today.minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
        app.container.db.expenses().insertAll(
            listOf(
                Expense(amountKopecks = 15_000_00, categoryId = catId, timestamp = old, place = "Ozon", note = "аванс за холодильник"),
                Expense(amountKopecks = 500_00, categoryId = catId, timestamp = now, note = "полка в холодильник"),
            )
        )

        vm.update("холодильник", BillingPeriod.containing(today, 1))

        val found = vm.elsewhere.filter { it.isNotEmpty() }.first()
        assertEquals(1, found.size)
        assertEquals(listOf("аванс за холодильник"), found.single().expenses.map { it.expense.note })
    }
}
