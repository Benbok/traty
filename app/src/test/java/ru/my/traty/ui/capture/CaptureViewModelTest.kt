package ru.my.traty.ui.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.ai.LlmClient
import ru.my.traty.data.Rule
import java.net.UnknownHostException

private class CannedLlm(private val reply: () -> String) : LlmClient {
    override val name = "Fake"
    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String = reply()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class CaptureViewModelTest {
    private lateinit var app: TratyApp
    private lateinit var vm: CaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        vm = CaptureViewModel(app)
    }

    @After
    fun tearDown() {
        TestTratyApp.llm = null
        Dispatchers.resetMain()
    }

    private suspend fun seedWithRule() {
        app.container.categories.seedIfEmpty()
        val cat = app.container.db.categories().getAllWithSubs().first { it.category.name == "Продукты" }
        app.container.db.rules().insert(Rule(pattern = "пятёрочка", categoryId = cat.category.id))
    }

    private val matched = """{"items":[{"type":"expense","amount":500,"place":"Пятёрочка"}]}"""

    @Test
    fun autosaveShowsSavedAndLeavesClosingToTheUi() = runTest {
        seedWithRule()
        TestTratyApp.llm = CannedLlm { matched }
        vm.onSpokenText("пятёрочка пятьсот").join()
        val saved = vm.ui.value as CaptureUi.Saved
        assertEquals(1, saved.result.saved.count)
        assertFalse(vm.finishRequested.value)
    }

    @Test
    fun editFromSavedOpensConfirmWithReplacesAndKeepsPanelOpen() = runTest {
        seedWithRule()
        TestTratyApp.llm = CannedLlm { matched }
        vm.onSpokenText("пятёрочка пятьсот").join()
        val saved = (vm.ui.value as CaptureUi.Saved).result.saved
        vm.editSaved().join()
        val confirm = vm.ui.value as CaptureUi.Confirm
        assertEquals(saved, confirm.draft.replaces)
        assertFalse(vm.finishRequested.value)
    }

    @Test
    fun noNetworkShowsQueuedNotice() = runTest {
        app.container.categories.seedIfEmpty()
        TestTratyApp.llm = CannedLlm { throw UnknownHostException("no net") }
        vm.onSpokenText("кофе 250").join()
        assertEquals(CaptureUi.Queued, vm.ui.value)
        assertFalse(vm.finishRequested.value)
    }

    @Test
    fun finishRequestsClose() {
        vm.finish()
        assertTrue(vm.finishRequested.value)
    }

    @Test
    fun confirmSaveFinishesImmediately() = runTest {
        app.container.categories.seedIfEmpty()
        TestTratyApp.llm = CannedLlm { """{"items":[{"type":"expense","amount":250,"category":"Кафе и рестораны"}]}""" }
        vm.onSpokenText("кофе 250").join()
        val confirm = vm.ui.value as CaptureUi.Confirm
        assertNotNull(confirm.draft)
        vm.save(confirm.draft).join()
        assertTrue(vm.finishRequested.value)
        assertEquals(1, app.container.db.expenses().getAllWithCategory().size)
    }

    @Test
    fun confirmWithNoValidAmountStaysOpen() = runTest {
        app.container.categories.seedIfEmpty()
        TestTratyApp.llm = CannedLlm { """{"items":[]}""" }
        vm.onSpokenText("эээ потом").join()
        val confirm = vm.ui.value as CaptureUi.Confirm
        vm.save(confirm.draft).join()
        assertFalse(vm.finishRequested.value)
        assertTrue(vm.ui.value is CaptureUi.Confirm)
        assertEquals("Нет корректных сумм — нечего сохранять", vm.error.value)
    }
}
