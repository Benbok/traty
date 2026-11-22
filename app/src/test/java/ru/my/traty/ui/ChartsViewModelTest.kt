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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.ui.charts.ChartsViewModel
import ru.my.traty.ui.charts.model.CashFlowCategoryKey
import ru.my.traty.ui.charts.model.CashFlowKind
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
            Category(name = "ТестКатегория", emoji = "🏷️", sortOrder = 0)
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

        assertEquals(
            20_000_00L,
            state.cashFlow.series.single().amounts.last(),
        )
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
    fun cashFlowShowsDailyTotalsInSeparateIncomeAndExpenseModes() = runTest(timeout = 20.seconds) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val zone = ZoneId.systemDefault()
        val db = app.container.db
        val groceries = db.categories().insert(Category(name = "Зарплата", emoji = "🛒", sortOrder = 0))
        val transport = db.categories().insert(Category(name = "Транспорт для теста", emoji = "🚕", sortOrder = 1))
        val games = db.categories().insert(Category(name = "Игры для теста", emoji = "🎮", sortOrder = 2))
        db.expenses().insertAll(listOf(
            Expense(amountKopecks = 4_000L, categoryId = games, timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli()),
            Expense(amountKopecks = 12_000L, categoryId = groceries, timestamp = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()),
            Expense(amountKopecks = 8_000L, categoryId = transport, timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli()),
        ))
        db.incomes().insert(ru.my.traty.data.Income(
            amountKopecks = 50_000L, source = "Зарплата", timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        ))

        vm.setCustomRange(yesterday, today)
        val groceriesKey = CashFlowCategoryKey.Expense(groceries)
        val transportKey = CashFlowCategoryKey.Expense(transport)
        val salaryKey = CashFlowCategoryKey.Income("зарплата")
        val allExpenses = vm.uiState.first { it.rangeStart == yesterday && it.cashFlowCategories.size == 3 }
        assertEquals(setOf(groceriesKey, transportKey, CashFlowCategoryKey.Expense(games)), allExpenses.cashFlowCategories.map { it.key }.toSet())
        assertEquals(listOf(yesterday, today), allExpenses.cashFlow.dates)
        assertEquals(listOf(12_000L, 12_000L), allExpenses.cashFlow.series.single().amounts)

        vm.toggleCashFlowCategory(groceriesKey)
        val one = vm.uiState.first { it.cashFlowCategoryKeys == setOf(groceriesKey) }
        assertEquals(listOf(12_000L, 0L), one.cashFlow.series.single().amounts)

        vm.toggleCashFlowCategory(transportKey)
        val two = vm.uiState.first { it.cashFlowCategoryKeys == setOf(groceriesKey, transportKey) }
        assertEquals(listOf(listOf(12_000L, 0L), listOf(0L, 8_000L)), two.cashFlow.series.map { it.amounts })
        assertEquals(listOf(12_000L), two.cashFlow.operationsByDay.getValue(yesterday).map { it.amount })
        assertEquals(24_000L, two.summary.totalCreditKopecks)

        vm.toggleCashFlowCategory(CashFlowCategoryKey.Expense(games))

        vm.setCashFlowKind(CashFlowKind.INCOME)
        val income = vm.uiState.first { it.cashFlowKind == CashFlowKind.INCOME }
        assertEquals(listOf(salaryKey), income.cashFlowCategories.map { it.key })
        assertEquals(listOf(0L, 50_000L), income.cashFlow.series.single().amounts)

        vm.setCashFlowKind(CashFlowKind.EXPENSE)
        val restored = vm.uiState.first { it.cashFlowKind == CashFlowKind.EXPENSE }
        assertEquals(setOf(groceriesKey, transportKey), restored.cashFlowCategoryKeys)

        vm.setCustomRange(today, today)
        val shorter = vm.uiState.first { it.rangeStart == today }
        assertEquals(restored.cashFlowCategoryKeys, shorter.cashFlowCategoryKeys)
        assertEquals(listOf(listOf(0L), listOf(8_000L)), shorter.cashFlow.series.map { it.amounts })
    }
}
