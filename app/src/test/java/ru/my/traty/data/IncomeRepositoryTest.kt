package ru.my.traty.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp

/** Свайп по пополнению в ленте «Трат» удаляет его через [IncomeRepository]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class IncomeRepositoryTest {
    private lateinit var app: TratyApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<TratyApp>()
    }

    @Test
    fun deletesUsualIncome() = runTest {
        val db = app.container.db
        val id = db.incomes().insert(Income(amountKopecks = 500_000, source = "Зарплата", timestamp = 1_000L))
        val other = db.incomes().insert(Income(amountKopecks = 10_000, source = "Кэшбэк", timestamp = 2_000L))

        app.container.incomes.delete(db.incomes().getAll().first { it.id == id })

        assertEquals(listOf(other), db.incomes().getAll().map { it.id })
    }

    @Test
    fun keepsReconcileIncome() = runTest {
        val db = app.container.db
        db.incomes().insert(
            Income(amountKopecks = 3_000, source = "Сверка", timestamp = 1_000L, origin = IncomeOrigin.RECONCILE),
        )

        app.container.incomes.delete(db.incomes().getAll().single())

        assertEquals(1, db.incomes().getAll().size)
    }
}
