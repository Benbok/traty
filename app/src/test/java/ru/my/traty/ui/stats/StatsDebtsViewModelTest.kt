package ru.my.traty.ui.stats

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Recurring
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtRepository
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class StatsDebtsViewModelTest {
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
    fun emptyWhenNoPurchases() = runTest(timeout = 20.seconds) {
        val vm = StatsDebtsViewModel(app)
        val o = vm.overview.first { it != null }
        assertTrue(o?.isEmpty == true)
    }

    @Test
    fun installmentAndLoanSubscriptionShowUp() = runTest(timeout = 20.seconds) {
        val c = app.container
        c.categories.seedIfEmpty()
        val cats = c.db.categories().getAllWithSubs()
        val home = cats.first().category.id
        val loans = cats.single { it.category.name == DebtRepository.INTEREST_CATEGORY }.category.id
        val today = LocalDate.now()
        c.debts.start(
            Debt(
                title = "Телевизор", categoryId = home, kind = DebtKind.INSTALLMENT.key, totalKopecks = 6_000_000,
                paymentsCount = 6, firstDueEpochDay = today.plusDays(10).toEpochDay(),
            ),
        )
        c.db.recurring().insert(
            Recurring(title = "Ипотека", amountKopecks = 3_500_000, categoryId = loans, periodUnit = "month", nextEpochDay = today.toEpochDay()),
        )

        val vm = StatsDebtsViewModel(app)
        val o = vm.overview.first { it?.isEmpty == false }
        assertEquals(6_000_000L, o?.leftToPayKopecks)
        assertEquals(1_000_000L, o?.dueSoonKopecks)
        assertEquals(listOf("Ипотека"), o?.subscriptionOnly?.map { it.title })
    }
}
