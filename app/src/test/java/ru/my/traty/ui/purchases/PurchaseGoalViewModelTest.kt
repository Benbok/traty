package ru.my.traty.ui.purchases

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class PurchaseGoalViewModelTest {
    @Test
    fun failedCreationLeavesTheFormOpenAndAValidGoalAppearsInTheFeed() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<TestTratyApp>()
        val vm = PurchaseGoalViewModel(app)
        val store = ViewModelStore().apply { put("purchases", vm) }
        try {
            val invalidSaved = CompletableDeferred<Unit>()
            vm.create("", 120_000, LocalDate.now().plusDays(10)) { invalidSaved.complete(Unit) }
            withTimeout(5000) { vm.error.filterNotNull().first() }
            assertFalse(invalidSaved.isCompleted)
            assertTrue(withTimeout(5000) { vm.goals.filterNotNull().first() }.isEmpty())

            val saved = CompletableDeferred<Unit>()
            vm.create("Наушники", 120_000, LocalDate.now().plusDays(10)) { saved.complete(Unit) }
            withTimeout(5000) { saved.await() }
            val goal = withTimeout(5000) { vm.goals.filterNotNull().first { it.isNotEmpty() } }.single()
            assertEquals("Наушники", goal.goal.title)
        } finally {
            store.clear()
            Dispatchers.resetMain()
            app.container.db.close()
        }
    }
}
