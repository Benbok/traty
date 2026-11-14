package ru.my.traty.ui

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.ai.EntryType
import ru.my.traty.ai.LlmClient
import ru.my.traty.capture.EntryDraft
import ru.my.traty.capture.EntryOrigin
import ru.my.traty.capture.EntryRow
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.Rule
import java.net.UnknownHostException

private class ReplyLlm(private val reply: () -> String) : LlmClient {
    override val name = "Fake"
    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String = reply()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class MainViewModelEntryTest {
    private lateinit var app: TratyApp
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        vm = MainViewModel(app)
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

    @Test
    fun spokenMixedPhraseOpensConfirm() = runTest {
        app.container.categories.seedIfEmpty()
        TestTratyApp.llm = ReplyLlm {
            """{"items":[{"type":"expense","amount":250,"category":"Кафе и рестораны"},{"type":"income","amount":80000,"source":"Зарплата"}]}"""
        }
        vm.onSpokenText("кофе 250 и зарплата 80 тысяч").join()
        val draft = vm.confirm
        assertNotNull(draft)
        assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), draft!!.rows.map { it.type })
        assertNull(vm.lastSaved)
    }

    @Test
    fun spokenMatchedPhraseAutosavesAndOffersEdit() = runTest {
        seedWithRule()
        TestTratyApp.llm = ReplyLlm { """{"items":[{"type":"expense","amount":500,"place":"Пятёрочка"}]}""" }
        vm.onSpokenText("пятёрочка пятьсот").join()
        assertEquals("Сохранено 1", vm.message)
        assertNull(vm.confirm)
        val last = vm.lastSaved
        assertNotNull(last)
        vm.editLastSaved().join()
        assertEquals(last!!.saved, vm.confirm!!.replaces)
        assertNull(vm.lastSaved)
    }

    @Test
    fun queuedPhraseShowsNotice() = runTest {
        app.container.categories.seedIfEmpty()
        TestTratyApp.llm = ReplyLlm { throw UnknownHostException("no net") }
        vm.onSpokenText("кофе 250").join()
        assertEquals("Нет сети, разберу позже", vm.message)
        assertEquals(1, vm.pending.first { it.isNotEmpty() }.size)
    }

    @Test
    fun saveEntrySavesMixedRowsAndClearsConfirm() = runTest {
        app.container.categories.seedIfEmpty()
        val base = EntryRow.empty()
        val draft = EntryDraft(
            rows = listOf(base.copy(amountText = "250"), base.copy(type = EntryType.INCOME, amountText = "1000", source = "Зарплата")),
            rawText = "x", origin = EntryOrigin.VOICE,
        )
        vm.confirm = draft
        vm.saveEntry(draft).join()
        assertNull(vm.confirm)
        assertEquals("Сохранено: 2", vm.message)
        assertEquals(1, app.container.db.expenses().getAllWithCategory().size)
        assertEquals(1, app.container.db.incomes().getAll().size)
    }

    @Test
    fun parsedPendingOpensConfirmAndIsDeletedAfterSave() = runTest {
        app.container.categories.seedIfEmpty()
        val draft = EntryDraft(listOf(EntryRow.empty().copy(amountText = "300")), "кофе", EntryOrigin.VOICE)
        val id = app.container.db.pendingPhrases().insert(
            PendingPhrase(text = "кофе 300", createdAt = 1, status = PendingStatus.PARSED, resultJson = Json.encodeToString(draft))
        )
        val pending = app.container.db.pendingPhrases().getByStatus(PendingStatus.PARSED).single()
        vm.openParsedPending(pending)
        assertEquals(draft, vm.confirm)
        vm.saveEntry(vm.confirm!!).join()
        assertTrue(app.container.db.pendingPhrases().getByStatus(PendingStatus.PARSED).none { it.id == id })
    }
}
