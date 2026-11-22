package ru.my.traty.ui

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Expense
import ru.my.traty.ui.charts.ChartsViewModel
import ru.my.traty.ui.charts.model.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ChartsViewModelTest {
    private lateinit var app: TratyApp
    private lateinit var vm: ChartsViewModel

    @Before
    fun setUp() {
        // runTest блокирует главный поток Robolectric, поэтому viewModelScope (Dispatchers.Main)
        // не получил бы управление и uiState никогда бы не обновился.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
        vm = ChartsViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPeriodSwitching() = runTest {
        vm.setPeriod(ChartPeriod.HALF_YEAR)
        assertEquals(ChartPeriod.HALF_YEAR, vm.period.value)

        vm.setPeriod(ChartPeriod.YEAR)
        assertEquals(ChartPeriod.YEAR, vm.period.value)
    }

    @Test
    fun testIncomeBalanceAndDelete() = runTest(timeout = 20.seconds) {
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

        // Пополнение появляется в базе (ввод — через строку «+» или сверку, не через экран графиков): 50 000 руб
        app.container.db.incomes().insert(
            ru.my.traty.data.Income(
                amountKopecks = 50_000_00L,
                source = "Зарплата",
                note = "Оклад за месяц",
                timestamp = timestamp,
            )
        )
        advanceUntilIdle()

        // Wait for uiState flow emission
        val state = vm.uiState
            .filter { it.summary.totalDebitKopecks > 0L && it.summary.totalCreditKopecks > 0L }
            .first()

        assertEquals(50_000_00L, state.summary.totalDebitKopecks)
        assertEquals(20_000_00L, state.summary.totalCreditKopecks)
        assertEquals(30_000_00L, state.summary.netBalanceKopecks)
        assertEquals(60, state.summary.savingsRatePercent) // (50 - 20) / 50 = 60%
        assertEquals(1, state.summary.incomeCount)
        assertEquals(1, state.summary.expenseCount)

        assertTrue(state.cashFlowPoints.isNotEmpty())

        val lastPoint = state.cashFlowPoints.last()
        assertEquals(50_000_00L, lastPoint.incomeKopecks)
        assertEquals(20_000_00L, lastPoint.expenseKopecks)
        assertEquals(30_000_00L, lastPoint.netKopecks)
    }

    @Test
    fun testSetCustomRange() = runTest(timeout = 10.seconds) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        val start = LocalDate.of(2026, 3, 10)
        val end = LocalDate.of(2026, 3, 25)

        vm.setCustomRange(start, end)
        advanceUntilIdle()

        val state = vm.uiState.filter { it.period == ChartPeriod.CUSTOM }.first()
        assertEquals(ChartPeriod.CUSTOM, state.period)
        assertEquals(start, state.rangeStart)
        assertEquals(end, state.rangeEnd)
    }

    @Test
    fun categoryFiltersOnlyCashFlowExpensesAndFollowsDateRange() = runTest(timeout = 20.seconds) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val zone = ZoneId.systemDefault()
        val db = app.container.db
        val groceries = db.categories().insert(ru.my.traty.data.Category(name = "Продукты для теста", emoji = "🛒", sortOrder = 0))
        val transport = db.categories().insert(ru.my.traty.data.Category(name = "Транспорт для теста", emoji = "🚕", sortOrder = 1))
        db.expenses().insertAll(listOf(
            Expense(amountKopecks = 12_000L, categoryId = groceries, timestamp = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()),
            Expense(amountKopecks = 8_000L, categoryId = transport, timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli()),
        ))
        db.incomes().insert(ru.my.traty.data.Income(
            amountKopecks = 50_000L, source = "Зарплата", timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        ))

        vm.setCustomRange(yesterday, today)
        val both = vm.uiState.first { it.rangeStart == yesterday && it.expenseCategories.size == 2 }
        assertEquals(setOf(groceries, transport), both.expenseCategories.map { it.id }.toSet())
        assertEquals(20_000L, both.cashFlowPoints.last().expenseKopecks)

        vm.setCashFlowCategory(groceries)
        val filtered = vm.uiState.first { it.cashFlowCategoryId == groceries }
        assertEquals(12_000L, filtered.cashFlowPoints.last().expenseKopecks)
        assertEquals(50_000L, filtered.cashFlowPoints.last().incomeKopecks)
        assertEquals(20_000L, filtered.summary.totalCreditKopecks)

        vm.setCustomRange(today, today)
        val todayOnly = vm.uiState.first { it.rangeStart == today && it.expenseCategories.size == 1 }
        assertEquals(listOf(transport), todayOnly.expenseCategories.map { it.id })
        assertEquals(null, todayOnly.cashFlowCategoryId)
        assertEquals(8_000L, todayOnly.cashFlowPoints.last().expenseKopecks)

        vm.setCashFlowCategory(transport)
        vm.uiState.first { it.cashFlowCategoryId == transport }
        vm.setCashFlowCategory(null)
        val all = vm.uiState.first { it.cashFlowCategoryId == null }
        assertEquals(8_000L, all.cashFlowPoints.last().expenseKopecks)
    }
}
