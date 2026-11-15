package ru.my.traty.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import ru.my.traty.ai.LlmHttpException
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.RecurringState
import ru.my.traty.data.Rule
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private class FakeLlm(private val reply: () -> String) : LlmClient {
    var lastSystem = ""
    override val name = "Fake"
    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String {
        lastSystem = system
        return reply()
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class QuickEntryTest {
    private lateinit var app: TratyApp
    private lateinit var db: AppDatabase
    private val zone: ZoneId = ZoneOffset.UTC
    private val spokenAt = 1_700_000_000_000L

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = app.container.db
    }

    private fun quickEntry(llm: LlmClient?) =
        QuickEntry(db, app.container.categories, app.container.settings, zone) { llm }

    private suspend fun seedWithRule(pattern: String, categoryName: String) {
        app.container.categories.seedIfEmpty()
        val cat = db.categories().getAllWithSubs().first { it.category.name == categoryName }
        db.rules().insert(Rule(pattern = pattern, categoryId = cat.category.id))
    }

    private val twoMatched =
        """{"items":[{"type":"expense","amount":500,"category":"Прочее","place":"Пятёрочка"},{"type":"expense","amount":300,"category":"Прочее","note":"пятёрочка молоко"}]}"""

    @Test
    fun autoSavesMatchedExpenses() = runTest {
        seedWithRule("пятёрочка", "Продукты")
        val outcome = quickEntry(FakeLlm { twoMatched }).process("пятёрочка пятьсот и триста", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.AutoSaved
        assertEquals(2, outcome.result.saved.count)
        val saved = db.expenses().getAllWithCategory()
        assertEquals(listOf("Продукты", "Продукты"), saved.map { it.categoryName })
        assertEquals(2, db.rules().getAll().single().hits)
    }

    @Test
    fun savedPaymentInRegularCategoryAppearsInSubscriptions() = runTest {
        app.container.categories.seedIfEmpty()
        val draft = EntryDraft(
            rows = listOf(
                EntryRow(
                    type = EntryType.EXPENSE, amountText = "990", category = "Связь и подписки", subcategory = "",
                    place = "Клод", note = "подписка", source = "", dateText = LocalDate.now().toString(),
                ),
                EntryRow(
                    type = EntryType.EXPENSE, amountText = "500", category = "Продукты", subcategory = "",
                    place = "Пятёрочка", note = "", source = "", dateText = LocalDate.now().toString(),
                ),
            ),
            rawText = "подписка Клод девятьсот девяносто и Пятёрочка пятьсот",
            origin = EntryOrigin.VOICE,
        )
        val qe = quickEntry(null)
        val result = qe.save(draft)

        // Сначала только вопрос: в «Подписках» пусто, пока пользователь не нажал «Добавить»
        assertEquals(listOf("Клод"), result.recurringOffers.map { it.title })
        assertEquals(listOf("Клод"), qe.recurringOffers.pending.value.map { it.title })
        assertTrue(db.recurring().getAll().isEmpty())
        assertEquals(2, db.expenses().getAllWithCategory().size)

        qe.recurringOffers.accept(qe.recurringOffers.pending.value.single())
        val payments = db.recurring().getAll()
        assertEquals(listOf("Клод"), payments.map { it.title })
        assertEquals(99_000L, payments.single().amountKopecks)
        assertTrue(qe.recurringOffers.pending.value.isEmpty())
    }

    @Test
    fun phraseWithoutPlaceOrNoteStillAppearsInSubscriptions() = runTest {
        app.container.categories.seedIfEmpty()
        val today = LocalDate.now().toString()
        val draft = EntryDraft(
            rows = listOf(
                EntryRow(EntryType.EXPENSE, "1200", "Связь и подписки", "Мобильная связь", "", "", "", today),
                EntryRow(EntryType.EXPENSE, "2100", "Связь и подписки", "Подписки", "", "", "", today),
            ),
            rawText = "мобильная связь 1200 и подписка две сто",
            origin = EntryOrigin.VOICE,
        )
        val qe = quickEntry(null)
        val result = qe.save(draft)

        assertEquals(listOf("Мобильная связь", "Подписки"), result.recurringOffers.map { it.title })
        qe.recurringOffers.pending.value.forEach { qe.recurringOffers.accept(it) }
        assertEquals(listOf(120_000L, 210_000L), db.recurring().getAll().map { it.amountKopecks })
    }

    @Test
    fun mixedPhraseNeedsConfirm() = runTest {
        seedWithRule("пятёрочка", "Продукты")
        val reply = """{"items":[{"type":"expense","amount":500,"place":"Пятёрочка"},{"type":"income","amount":80000,"source":"Зарплата"}]}"""
        val outcome = quickEntry(FakeLlm { reply }).process("пятёрочка 500 и зарплата", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), outcome.draft.rows.map { it.type })
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
        assertTrue(db.incomes().getAll().isEmpty())
    }

    @Test
    fun unknownTypeNeverAutosaves() = runTest {
        seedWithRule("пятёрочка", "Продукты")
        val reply = """{"items":[{"type":"goal_deposit","amount":500,"place":"Пятёрочка"}]}"""
        val outcome = quickEntry(FakeLlm { reply }).process("отложил", EntryOrigin.VOICE, spokenAt)
        assertTrue(outcome is EntryOutcome.NeedsConfirm)
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun networkErrorQueues() = runTest {
        app.container.categories.seedIfEmpty()
        val outcome = quickEntry(FakeLlm { throw UnknownHostException("no net") }).process("кофе 250", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.Queued
        val waiting = db.pendingPhrases().getByStatus(PendingStatus.WAITING).single()
        assertEquals(outcome.pendingId, waiting.id)
        assertEquals("кофе 250", waiting.text)
        assertEquals(spokenAt, waiting.createdAt)
    }

    @Test
    fun httpErrorFallsBackOfflineAndConfirms() = runTest {
        app.container.categories.seedIfEmpty()
        val outcome = quickEntry(FakeLlm { throw LlmHttpException(401, "HTTP 401") }).process("кофе 250", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        assertTrue(outcome.notice!!.contains("Использован простой разбор"))
        assertEquals("250", outcome.draft.rows.single().amountText)
        assertTrue(db.pendingPhrases().getByStatus(PendingStatus.WAITING).isEmpty())
    }

    @Test
    fun noClientUsesOfflineAndAlwaysConfirms() = runTest {
        seedWithRule("кофе", "Кафе и рестораны")
        val outcome = quickEntry(null).process("кофе 250", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        assertEquals("250", outcome.draft.rows.single().amountText)
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun garbageGivesSingleEmptyRowAndKeepsText() = runTest {
        app.container.categories.seedIfEmpty()
        val outcome = quickEntry(FakeLlm { """{"items":[]}""" }).process("эээ потом", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        assertEquals(1, outcome.draft.rows.size)
        assertEquals("", outcome.draft.rows.single().amountText)
        assertEquals("эээ потом", outcome.draft.rawText)
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun replaceSwapsSavedEntries() = runTest {
        seedWithRule("пятёрочка", "Продукты")
        val qe = quickEntry(FakeLlm { twoMatched })
        val auto = qe.process("пятёрочка", EntryOrigin.VOICE, spokenAt) as EntryOutcome.AutoSaved
        val oldIds = auto.result.saved.expenseIds.toSet()
        val draft = qe.draftFor(auto.result.saved, "пятёрочка", EntryOrigin.VOICE)
        assertEquals(auto.result.saved, draft.replaces)
        assertEquals(listOf("500", "300"), draft.rows.map { it.amountText })
        qe.save(draft.copy(rows = listOf(draft.rows[0].copy(amountText = "700"))))
        val now = db.expenses().getAllWithCategory()
        assertEquals(1, now.size)
        assertEquals(70_000L, now.single().expense.amountKopecks)
        assertTrue(now.none { it.expense.id in oldIds })
    }

    @Test
    fun savesIncomeRowsAsIncomes() = runTest {
        app.container.categories.seedIfEmpty()
        val qe = quickEntry(null)
        val draft = qe.emptyDraft(EntryOrigin.VOICE, "зарплата").let {
            it.copy(rows = listOf(it.rows.single().copy(type = EntryType.INCOME, amountText = "80000", source = "Зарплата")))
        }
        val result = qe.save(draft)
        assertEquals(1, result.saved.incomeIds.size)
        assertEquals(8_000_000L, db.incomes().getAll().single().amountKopecks)
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun saveWithNoValidAmountsChangesNothing() = runTest {
        app.container.categories.seedIfEmpty()
        val qe = quickEntry(null)
        val result = qe.save(qe.emptyDraft(EntryOrigin.MANUAL))
        assertEquals(0, result.saved.count)
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
    }

    @Test
    fun promptTodayIsSpokenAtDay() = runTest {
        app.container.categories.seedIfEmpty()
        val llm = FakeLlm { """{"items":[]}""" }
        val at = LocalDate.of(2026, 11, 13).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        quickEntry(llm).process("вчера кофе 250", EntryOrigin.VOICE, at)
        assertTrue(llm.lastSystem.contains("2026-11-13"))
    }

    @Test
    fun changesEmitsAfterSave() = runTest {
        seedWithRule("пятёрочка", "Продукты")
        val qe = quickEntry(FakeLlm { twoMatched })
        var emitted = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { qe.changes.collect { emitted = true } }
        qe.process("пятёрочка", EntryOrigin.VOICE, spokenAt)
        assertTrue(emitted)
    }

    @Test
    fun petShopPhraseCreatesSubcategoryAndKeepsNote() = runTest {
        app.container.categories.seedIfEmpty()
        val reply = """{"items":[{"type":"expense","amount":731,"category":"Питомцы","subcategory":"Зоомагазин","place":null,"note":"лекарство для кошки Чуни от блох","date":"2023-11-13"}]}"""
        val outcome = quickEntry(FakeLlm { reply })
            .process("зоомагазин вчера 731 лекарство для кошки Чуни от блох", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        val row = outcome.draft.rows.single()
        assertEquals("Питомцы", row.category)
        assertEquals("Зоомагазин", row.subcategory)
        assertEquals("лекарство для кошки Чуни от блох", row.note)
        // spokenAt = 2023-11-14 (UTC), «вчера» — 13-е
        assertEquals("2023-11-13", row.dateText)

        val result = quickEntry(null).save(outcome.draft)
        assertEquals(listOf("Питомцы › Зоомагазин"), result.newSubcategories)
        val saved = db.expenses().getAllWithCategory().single()
        assertEquals("Питомцы", saved.categoryName)
        assertEquals("Зоомагазин", saved.subcategoryName)
        assertEquals("лекарство для кошки Чуни от блох", saved.expense.note)
        assertEquals(null, saved.expense.place)
    }

    @Test
    fun offlinePetShopPhraseCreatesMissingCategory() = runTest {
        // Категорий нет совсем: «Питомцы» и «Зоомагазин» создадутся при сохранении
        val outcome = quickEntry(null).process("зоомагазин вчера 731 лекарство для кошки Чуни от блох", EntryOrigin.VOICE, spokenAt)
        outcome as EntryOutcome.NeedsConfirm
        val row = outcome.draft.rows.single()
        assertEquals("Питомцы", row.category)
        assertEquals(NewCategory("Питомцы", "🐾"), row.suggestedCategory)
        val result = quickEntry(null).save(outcome.draft)
        assertEquals(listOf("Питомцы"), result.newCategories)
        val saved = db.expenses().getAllWithCategory().single()
        assertEquals("Зоомагазин", saved.subcategoryName)
        assertEquals("лекарство для кошки Чуни от блох", saved.expense.note)
    }

    @Test
    fun declinedRecurringOfferKeepsExpenseAndIsNotAskedAgain() = runTest {
        app.container.categories.seedIfEmpty()
        val row = EntryRow(EntryType.EXPENSE, "990", "Связь и подписки", "", "Клод", "", "", LocalDate.now().toString())
        val draft = EntryDraft(listOf(row), "Клод девятьсот девяносто", EntryOrigin.VOICE)
        val qe = quickEntry(null)
        qe.save(draft)
        qe.recurringOffers.decline(qe.recurringOffers.pending.value.single())

        assertEquals(1, db.expenses().getAllWithCategory().size)
        assertEquals(listOf(RecurringState.DISMISSED), db.recurring().getAll().map { it.state })
        assertTrue(qe.save(draft).recurringOffers.isEmpty())
        assertTrue(qe.recurringOffers.pending.value.isEmpty())
    }

    @Test
    fun sameRecurringOfferIsAskedOnce() = runTest {
        app.container.categories.seedIfEmpty()
        val row = EntryRow(EntryType.EXPENSE, "990", "Связь и подписки", "", "Клод", "", "", LocalDate.now().toString())
        val qe = quickEntry(null)
        qe.save(EntryDraft(listOf(row, row), "Клод и снова Клод", EntryOrigin.VOICE))
        qe.save(EntryDraft(listOf(row), "Клод", EntryOrigin.VOICE))
        assertEquals(1, qe.recurringOffers.pending.value.size)
    }
}
