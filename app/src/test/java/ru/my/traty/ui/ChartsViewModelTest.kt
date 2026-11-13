package ru.my.traty.ui

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TratyApp
import ru.my.traty.data.Expense
import ru.my.traty.ui.charts.ChartsViewModel
import ru.my.traty.ui.charts.model.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TratyApp::class)
class ChartsViewModelTest {
    private lateinit var app: TratyApp
    private lateinit var vm: ChartsViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<TratyApp>()
        vm = ChartsViewModel(app)
    }

    @Test
    fun testPeriodSwitching() = runTest {
        vm.setPeriod(ChartPeriod.HALF_YEAR)
        assertEquals(ChartPeriod.HALF_YEAR, vm.period.value)

        vm.setPeriod(ChartPeriod.YEAR)
        assertEquals(ChartPeriod.YEAR, vm.period.value)
    }

    @Test
    fun testAddIncomeAndCalculateBalance() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli()

        // Insert category & expense
        val catId = app.container.db.categories().insert(
            ru.my.traty.data.Category(name = "ТестКатегория", emoji = "🏷️", sortOrder = 0)
        )
        app.container.db.expenses().insertAll(
            listOf(
                Expense(
                    amountKopecks = 20_000_00L, // 20 000 руб
                    categoryId = catId,
                    timestamp = timestamp,
                )
            )
        )

        // Add income via ViewModel: 50 000 руб
        vm.addIncome(50000.0, "Зарплата", "Оклад за месяц", today)
        advanceUntilIdle()

        // Wait for uiState flow emission
        val state = vm.uiState.filter { it.summary.totalDebitKopecks > 0L }.first()

        assertEquals(50_000_00L, state.summary.totalDebitKopecks)
        assertEquals(20_000_00L, state.summary.totalCreditKopecks)
        assertEquals(30_000_00L, state.summary.netBalanceKopecks)
        assertEquals(60, state.summary.savingsRatePercent) // (50 - 20) / 50 = 60%
        assertEquals(1, state.summary.incomeCount)
        assertEquals(1, state.summary.expenseCount)

        assertTrue(state.balancePoints.isNotEmpty())
        assertTrue(state.debitCreditBars.isNotEmpty())

        val lastPoint = state.balancePoints.last()
        assertEquals(30_000_00L, lastPoint.cumulativeBalanceKopecks)

        // Delete income
        val income = state.recentIncomes.first()
        vm.deleteIncome(income)
        advanceUntilIdle()

        val afterDelete = vm.uiState.filter { it.summary.totalDebitKopecks == 0L }.first()
        assertEquals(0L, afterDelete.summary.totalDebitKopecks)
        assertEquals(-20_000_00L, afterDelete.summary.netBalanceKopecks)
    }
}
