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
}
