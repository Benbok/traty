package ru.my.traty.ui.reconcile

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ReconcileViewModelTest {
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

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertNotNull("timeout: $what", if (cond()) true else null)
    }

    @Test
    fun redoAfterFirstConfirmShowsJustSavedAmountAndNotFirst() {
        val vm = ReconcileViewModel(app)
        waitFor("loaded") { vm.loaded }
        vm.setAmount(vm.lines.first().id, "25 500")
        vm.confirm()
        waitFor("result") { vm.result != null }

        vm.redo()

        assertEquals(25_500_00L, vm.replacedActualKopecks)
        assertFalse(vm.isFirst)
    }
}
