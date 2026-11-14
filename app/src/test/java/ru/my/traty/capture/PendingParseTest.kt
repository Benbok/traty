package ru.my.traty.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.Rule
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneOffset

private class CountingLlm(private val reply: () -> String) : LlmClient {
    var calls = 0
    var lastSystem = ""
    override val name = "Fake"
    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String {
        calls++
        lastSystem = system
        return reply()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class PendingParseTest {
    private lateinit var app: TratyApp
    private lateinit var db: AppDatabase
    private val zone = ZoneOffset.UTC
    private val spokenAt = LocalDate.of(2026, 11, 13).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = app.container.db
    }

    private fun quickEntry(llm: LlmClient) = QuickEntry(db, app.container.categories, app.container.settings, zone) { llm }

    private suspend fun seedWithRule() {
        app.container.categories.seedIfEmpty()
        val cat = db.categories().getAllWithSubs().first { it.category.name == "Продукты" }
        db.rules().insert(Rule(pattern = "пятёрочка", categoryId = cat.category.id))
    }

    private suspend fun queue(text: String = "пятёрочка 500", createdAt: Long = spokenAt) =
        db.pendingPhrases().insert(PendingPhrase(text = text, createdAt = createdAt))

    @Test
    fun matchedPhraseIsSavedAndRemoved() = runTest {
        seedWithRule()
        queue()
        val llm = CountingLlm { """{"items":[{"type":"expense","amount":500,"place":"Пятёрочка"}]}""" }
        assertEquals(1, quickEntry(llm).processPending())
        assertEquals(1, db.expenses().getAllWithCategory().size)
        assertTrue(db.pendingPhrases().getByStatus(PendingStatus.WAITING).isEmpty())
        assertTrue(db.pendingPhrases().getByStatus(PendingStatus.PARSED).isEmpty())
    }

    @Test
    fun mixedPhraseBecomesParsedWithDraftJson() = runTest {
        seedWithRule()
        queue("пятёрочка 500 и зарплата")
        val llm = CountingLlm {
            """{"items":[{"type":"expense","amount":500,"place":"Пятёрочка"},{"type":"income","amount":80000,"source":"Зарплата"}]}"""
        }
        assertEquals(1, quickEntry(llm).processPending())
        val parsed = db.pendingPhrases().getByStatus(PendingStatus.PARSED).single()
        val draft = Json.decodeFromString<EntryDraft>(parsed.resultJson!!)
        assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), draft.rows.map { it.type })
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun stillOfflineStaysWaitingWithoutDuplicates() = runTest {
        seedWithRule()
        queue()
        val llm = CountingLlm { throw UnknownHostException("no net") }
        assertEquals(0, quickEntry(llm).processPending())
        assertEquals(1, db.pendingPhrases().getByStatus(PendingStatus.WAITING).size)
    }

    @Test
    fun stopsAtFirstNetworkError() = runTest {
        seedWithRule()
        queue("первая", createdAt = spokenAt)
        queue("вторая", createdAt = spokenAt + 1)
        val llm = CountingLlm { throw UnknownHostException("no net") }
        quickEntry(llm).processPending()
        assertEquals(1, llm.calls)
    }

    @Test
    fun relativeDateUsesCreatedAtNotNow() = runTest {
        seedWithRule()
        queue("вчера кофе 250")
        val llm = CountingLlm { """{"items":[]}""" }
        quickEntry(llm).processPending()
        assertTrue(llm.lastSystem.contains("2026-11-13"))
    }
}
