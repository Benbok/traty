package ru.my.traty.ui.apartments

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.data.apartments.Apartment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ApartmentsViewModelTest {
    @Test fun failedInputKeepsHistoryAndResetsBusy() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<TestTratyApp>()
        val vm = ApartmentsViewModel(app)
        val store = ViewModelStore().apply { put("apartments", vm) }
        try {
            val done = CompletableDeferred<Unit>()
            vm.save(Apartment(name = "Дом")) { done.complete(Unit) }
            withTimeout(5000) { done.await() }
            assertEquals("Дом", vm.snapshot.value.apartments.single().name)
            vm.save(Apartment(name = "")) { fail("Неверный ввод не должен закрывать форму") }
            withTimeout(5000) { while (vm.busy) delay(1) }
            assertFalse(vm.busy)
            assertNotNull(vm.message)
            assertEquals(1, vm.snapshot.value.apartments.size)
        } finally { store.clear(); Dispatchers.resetMain(); app.container.db.close() }
    }
}
