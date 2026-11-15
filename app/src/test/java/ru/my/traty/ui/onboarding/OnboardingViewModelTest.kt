package ru.my.traty.ui.onboarding

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.Expense

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class OnboardingViewModelTest {
    private lateinit var app: TratyApp

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext<TratyApp>()
        // Флаг лежит в файле DataStore и может пережить тест: начинаем с «ещё не видели»
        runBlocking { app.container.settings.setOnboardingSeen(false) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertNotNull("timeout: $what", if (cond()) true else null)
    }

    private fun seen(): Boolean = runBlocking { app.container.settings.onboardingSeen.first() }

    @Test
    fun newPersonSeesOnboardingAndFinishRemembersIt() {
        val vm = OnboardingViewModel(app)
        waitFor("checked") { vm.visible.value != null }
        assertEquals(true, vm.visible.value)

        vm.finish()

        assertEquals(false, vm.visible.value)
        waitFor("saved") { seen() }
        val again = OnboardingViewModel(app)
        waitFor("checked again") { again.visible.value != null }
        assertEquals(false, again.visible.value)
    }

    @Test
    fun personWithRecordsDoesNotSeeItButCanOpenFromSettings() {
        runBlocking {
            val categoryId = app.container.categories.addCategory("Кафе", "☕")
            app.container.db.expenses().insert(Expense(amountKopecks = 250_00, categoryId = categoryId, timestamp = 1L))
        }
        val vm = OnboardingViewModel(app)
        waitFor("checked") { vm.visible.value != null }
        assertEquals(false, vm.visible.value)
        assertTrue(seen())

        vm.show()

        assertEquals(true, vm.visible.value)
    }
}
