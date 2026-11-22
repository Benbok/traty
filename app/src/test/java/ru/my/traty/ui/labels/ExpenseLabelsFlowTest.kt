package ru.my.traty.ui.labels

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.labels.ExpenseLabel
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ExpenseLabelsFlowTest {
    @Test
    fun archivedImpulsiveCategoryKeepsHistoricalLabelsAndRespectsGlobalSwitch() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TestTratyApp>()
        val c = app.container
        val zone = ZoneId.of("Europe/Moscow")
        try {
            c.settings.setLabelsEnabled(true)
            val category = c.db.categories().insert(
                Category(name = "Игры", emoji = "⚡", isImpulsive = true, isArchived = true),
            )
            val expense = c.db.expenses().insert(
                Expense(
                    amountKopecks = 30_000, categoryId = category,
                    timestamp = LocalDate.of(2026, 11, 20).atTime(10, 51).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
            val labels = withTimeout(5000) { expenseLabelsFlow(c, zone).first() }
            assertTrue(labels[expense].orEmpty().any { it.label == ExpenseLabel.IMPULSIVE })
            c.settings.setLabelsEnabled(false)
            assertTrue(withTimeout(5000) { expenseLabelsFlow(c, zone).first() }.isEmpty())
        } finally {
            c.db.close()
        }
    }
}
