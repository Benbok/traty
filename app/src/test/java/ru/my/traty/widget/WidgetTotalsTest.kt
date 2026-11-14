package ru.my.traty.widget

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.AppSettings
import ru.my.traty.data.Expense
import ru.my.traty.data.Income
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class WidgetTotalsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)

    private fun noon(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun countsTodayAndBillingPeriodExpensesOnly() = runTest {
        val app = ApplicationProvider.getApplicationContext<TratyApp>()
        val db = app.container.db
        app.container.categories.seedIfEmpty()
        val cat = db.categories().getAllWithSubs().first().category.id
        fun expense(day: LocalDate, rub: Long) = Expense(amountKopecks = rub * 100, categoryId = cat, timestamp = noon(day))

        db.expenses().insert(expense(LocalDate.of(2026, 11, 9), 100))   // прошлый период (период начинается 10-го)
        db.expenses().insert(expense(LocalDate.of(2026, 11, 10), 200))
        db.expenses().insert(expense(LocalDate.of(2026, 11, 12), 50))   // как запись «Неучтённые»: считается
        db.expenses().insert(expense(today, 300))
        db.incomes().insert(Income(amountKopecks = 9_999_900, timestamp = noon(today)))  // пополнения не учитываются

        val totals = loadWidgetTotals(db, AppSettings(periodStartDay = 10), today, zone)

        assertEquals(30_000L, totals.todayKopecks)
        assertEquals(55_000L, totals.periodKopecks)
    }

    @Test
    fun emptyDatabaseGivesZeros() = runTest {
        val app = ApplicationProvider.getApplicationContext<TratyApp>()
        val totals = loadWidgetTotals(app.container.db, AppSettings(), today, zone)
        assertEquals(WidgetTotals(0, 0), totals)
    }

    @Test
    fun loadWidgetDataIncludesTotalsAndTodayExpenses() = runTest {
        val app = ApplicationProvider.getApplicationContext<TratyApp>()
        val db = app.container.db
        app.container.categories.seedIfEmpty()
        val cat = db.categories().getAllWithSubs().first().category.id

        val morningToday = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val eveningToday = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = today.minusDays(1).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()

        db.expenses().insert(Expense(amountKopecks = 150_00, categoryId = cat, place = "Кофейня", timestamp = morningToday))
        db.expenses().insert(Expense(amountKopecks = 500_00, categoryId = cat, place = "Супермаркет", timestamp = eveningToday))
        db.expenses().insert(Expense(amountKopecks = 200_00, categoryId = cat, place = "Вчерашняя трата", timestamp = yesterday))

        val data = loadWidgetData(db, AppSettings(periodStartDay = 1), today, zone, limit = 5)

        assertEquals(650_00L, data.totals.todayKopecks)
        assertEquals(2, data.todayExpenses.size)
        // Проверяем сортировку: сначала более свежая (eveningToday), затем утренняя (morningToday)
        assertEquals(500_00L, data.todayExpenses[0].expense.amountKopecks)
        assertEquals("Супермаркет", data.todayExpenses[0].expense.place)
        assertEquals(150_00L, data.todayExpenses[1].expense.amountKopecks)
        assertEquals("Кофейня", data.todayExpenses[1].expense.place)
    }

    @Test
    fun widgetReceiversProvideExpectedWidgets() {
        org.junit.Assert.assertNotNull(TratyWidgetReceiver().glanceAppWidget)
        org.junit.Assert.assertNotNull(TratyRecordWidgetReceiver().glanceAppWidget)
        org.junit.Assert.assertTrue(TratyWidgetReceiver().glanceAppWidget is TratyWidget)
        org.junit.Assert.assertTrue(TratyRecordWidgetReceiver().glanceAppWidget is TratyRecordWidget)
    }
}
