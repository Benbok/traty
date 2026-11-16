package ru.my.traty.capture

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
import ru.my.traty.ai.EntryType
import ru.my.traty.ai.LlmClient
import ru.my.traty.data.AppDatabase
import java.time.ZoneId
import java.time.ZoneOffset

/** Товары из фразы сохраняются позициями траты и возвращаются в «Изменить». */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class QuickEntryItemsTest {
    private lateinit var app: TratyApp
    private lateinit var db: AppDatabase
    private val zone: ZoneId = ZoneOffset.UTC
    private val spokenAt = 1_700_000_000_000L

    private class Reply(private val json: String) : LlmClient {
        override val name = "Fake"
        override suspend fun complete(system: String, user: String, jsonMode: Boolean) = json
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = app.container.db
    }

    private fun quickEntry(llm: LlmClient?) = QuickEntry(db, app.container.categories, app.container.settings, zone) { llm }

    private val eldorado =
        """{"items":[{"type":"expense","amount":45000,"category":"Дом и быт","subcategory":"Техника","place":"Эльдорадо","note":"телефон, чехол, клавиатура",
        "goods":[{"name":"телефон","price":40000},{"name":"чехол","price":2000},{"name":"клавиатура","price":3000}]}]}"""

    @Test
    fun savedExpenseKeepsItemsAndEditGetsThemBack() = runTest {
        app.container.categories.seedIfEmpty()
        val text = "Эльдорадо 45 тысяч телефон 40 тысяч чехол 2 тысячи клавиатура 3 тысячи"
        val qe = quickEntry(Reply(eldorado))
        val draft = when (val o = qe.process(text, EntryOrigin.VOICE, spokenAt)) {
            is EntryOutcome.NeedsConfirm -> o.draft
            is EntryOutcome.AutoSaved -> o.draft
            is EntryOutcome.Queued -> error("не должно уйти в очередь")
        }
        assertEquals(3, draft.rows.single().items.size)
        val result = qe.save(draft)
        val id = result.saved.expenseIds.single()
        val items = app.container.expenseItems.getFor(id)
        assertEquals(listOf("Телефон", "Чехол", "Клавиатура"), items.map { it.name })
        assertEquals(4_500_000L, items.sumOf { it.priceKopecks ?: 0L })

        // «Изменить»: черновик с теми же товарами, замена не дублирует позиции
        val again = qe.draftFor(result.saved, text, EntryOrigin.VOICE)
        assertEquals(EntryType.EXPENSE, again.rows.single().type)
        assertEquals(3, again.rows.single().items.size)
        val replaced = qe.save(again)
        assertEquals(3, app.container.expenseItems.getFor(replaced.saved.expenseIds.single()).size)
        assertEquals(3, db.expenseItems().getSales(0, Long.MAX_VALUE).size)
    }
}
