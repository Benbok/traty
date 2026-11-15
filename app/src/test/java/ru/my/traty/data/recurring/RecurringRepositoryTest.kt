package ru.my.traty.data.recurring

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RecurringRepository
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 14)
    private var categoryId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CategoryRepository(db).seedIfEmpty()
        categoryId = db.categories().getAllWithSubs().single { it.category.name == RecurringRepository.SUBSCRIPTION_CATEGORY }.category.id
        repo = RecurringRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addCharges(months: IntRange, place: String = "Яндекс Плюс") {
        for (m in months) {
            db.expenses().insert(
                Expense(
                    amountKopecks = 39_900, categoryId = categoryId, place = place, source = ExpenseSource.VOICE,
                    timestamp = LocalDate.of(2026, m, 5).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                )
            )
        }
    }

    private suspend fun suggestions() = RecurringRepository.suggest(
        db.expenses().getAllWithCategory(), db.recurring().getAll(), db.categories().getAllWithSubs(), today, zone,
    )

    @Test
    fun acceptedSuggestionBecomesPaymentAndDisappearsFromSuggestions() = runTest {
        addCharges(9..11)
        val candidate = suggestions().single()
        repo.accept(candidate, today)

        assertTrue(suggestions().isEmpty())
        val saved = db.recurring().observeVisible().first().single()
        assertEquals("Яндекс Плюс", saved.recurring.title)
        assertEquals(LocalDate.of(2026, 12, 5).toEpochDay(), saved.recurring.nextEpochDay)
        assertTrue(saved.recurring.autoDetected)
    }

    @Test
    fun dismissedSuggestionIsRememberedButNotShown() = runTest {
        addCharges(9..11)
        repo.dismiss(suggestions().single(), today)

        assertTrue(suggestions().isEmpty())
        assertTrue(db.recurring().observeVisible().first().isEmpty())
        assertEquals(RecurringState.DISMISSED, db.recurring().getAll().single().state)
    }

    @Test
    fun removedPaymentIsNotSuggestedAgain() = runTest {
        addCharges(9..11)
        repo.accept(suggestions().single(), today)
        repo.remove(db.recurring().getAll().single())

        assertTrue(db.recurring().observeVisible().first().isEmpty())
        assertTrue(suggestions().isEmpty())
    }

    @Test
    fun manualPaymentGetsKeyAndBlocksSameSuggestion() = runTest {
        addCharges(9..11)
        repo.save(
            Recurring(
                title = " яндекс плюс ", amountKopecks = 39_900, categoryId = categoryId, periodUnit = "month",
                nextEpochDay = LocalDate.of(2026, 12, 5).toEpochDay(),
            )
        )
        val saved = db.recurring().getAll().single()
        assertEquals("$categoryId|яндекс плюс", saved.detectKey)
        assertTrue(suggestions().isEmpty())
    }

    @Test
    fun pauseAndResumeKeepThePayment() = runTest {
        addCharges(9..11)
        repo.accept(suggestions().single(), today)
        val item = db.recurring().getAll().single()

        repo.setPaused(item, true)
        assertEquals(RecurringState.PAUSED, db.recurring().getAll().single().state)
        repo.setPaused(db.recurring().getAll().single(), false)
        assertEquals(RecurringState.ACTIVE, db.recurring().getAll().single().state)
    }

    @Test
    fun markPaidCreatesExpenseAndMovesToNextPeriod() = runTest {
        val id = repo.save(
            Recurring(
                title = "Яндекс Плюс", amountKopecks = 39_900, categoryId = categoryId, periodUnit = "month",
                nextEpochDay = LocalDate.of(2026, 11, 14).toEpochDay(), place = "Яндекс Плюс",
            )
        )
        assertTrue(repo.markPaid(id, today, zone))

        val expense = db.expenses().getAllWithCategory().single().expense
        assertEquals(39_900L, expense.amountKopecks)
        assertEquals(ExpenseSource.RECURRING, expense.source)
        assertEquals("Яндекс Плюс", expense.place)
        assertEquals(categoryId, expense.categoryId)
        assertEquals(LocalDate.of(2026, 12, 14).toEpochDay(), db.recurring().getById(id)!!.nextEpochDay)
    }

    @Test
    fun markPaidIgnoresPausedAndMissingPayments() = runTest {
        val id = repo.save(
            Recurring(
                title = "Кино", amountKopecks = 10_000, categoryId = categoryId, periodUnit = "month",
                nextEpochDay = today.toEpochDay(), state = RecurringState.PAUSED,
            )
        )
        assertEquals(false, repo.markPaid(id, today, zone))
        assertEquals(false, repo.markPaid(9_999, today, zone))
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
        assertEquals(today.toEpochDay(), db.recurring().getById(id)!!.nextEpochDay)
    }

    @Test
    fun paidExpenseDoesNotBringTheSuggestionBack() = runTest {
        addCharges(9..11)
        repo.accept(suggestions().single(), today)
        val id = db.recurring().getAll().single().id
        repo.markPaid(id, today, zone)
        assertTrue(suggestions().isEmpty())
    }

    private fun spent(
        place: String?, note: String?, day: LocalDate, category: Long = categoryId, kopecks: Long = 99_000,
        source: String = ExpenseSource.VOICE,
    ) = Expense(
        id = 1, amountKopecks = kopecks, categoryId = category, place = place, note = note, source = source,
        timestamp = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    /** Трата сохранена, пользователь ответил «Добавить». */
    private suspend fun savedAndAccepted(
        expense: Expense, category: String, sub: String? = null,
    ): String? = repo.onExpenseSaved(expense, category, zone, sub)?.also { repo.acceptOffer(it) }?.title

    @Test
    fun regularPaymentIsOnlyOfferedNotAdded() = runTest {
        val offer = requireNotNull(repo.onExpenseSaved(spent("Клод", "подписка", today), "Связь и подписки", zone))
        assertEquals("Клод", offer.title)
        assertEquals(99_000L, offer.amountKopecks)
        assertEquals("month", offer.periodUnit)
        assertEquals(LocalDate.of(2026, 12, 14).toEpochDay(), offer.nextEpochDay)
        assertTrue(offer.autoDetected)
        // Без ответа пользователя в «Подписках» ничего нет
        assertTrue(db.recurring().getAll().isEmpty())
    }

    @Test
    fun acceptedOfferBecomesActivePaymentOnce() = runTest {
        val offer = requireNotNull(repo.onExpenseSaved(spent("Клод", "подписка", today), "Связь и подписки", zone))
        assertTrue(repo.acceptOffer(offer) != null)
        assertEquals(null, repo.acceptOffer(offer))
        val saved = db.recurring().getAll().single()
        assertEquals(RecurringState.ACTIVE, saved.state)
        assertEquals("Клод", saved.title)
    }

    @Test
    fun declinedOfferIsNotOfferedAgain() = runTest {
        val offer = requireNotNull(repo.onExpenseSaved(spent("Клод", null, today), "Связь и подписки", zone))
        repo.declineOffer(offer)
        assertEquals(RecurringState.DISMISSED, db.recurring().getAll().single().state)
        assertEquals(null, repo.onExpenseSaved(spent("Клод", null, LocalDate.of(2026, 12, 14)), "Связь и подписки", zone))
        assertEquals(0, RecurringSummary.totals(db.recurring().getAll()).count)
    }

    @Test
    fun noteIsTheTitleWhenThereIsNoPlace() = runTest {
        assertEquals("Оплата связи", repo.onExpenseSaved(spent(null, "оплата связи", today), "Связь и подписки", zone)?.title)
    }

    @Test
    fun regularCategoriesAreNotLimitedToSubscriptions() = runTest {
        val housing = db.categories().getAllWithSubs().single { it.category.name == "Жильё" }.category.id
        assertEquals("Аренда", repo.onExpenseSaved(spent(null, "аренда", today, housing), "Жильё", zone)?.title)
        val loans = db.categories().getAllWithSubs().single { it.category.name == "Долги и кредиты" }.category.id
        assertEquals("Ипотека", repo.onExpenseSaved(spent("Ипотека", null, today, loans), "Долги и кредиты", zone)?.title)
    }

    @Test
    fun ordinaryCategoryIsIgnoredUnlessItSaysSubscription() = runTest {
        val food = db.categories().getAllWithSubs().single { it.category.name == "Продукты" }.category.id
        assertEquals(null, repo.onExpenseSaved(spent("Пятёрочка", null, today, food), "Продукты", zone))
        assertEquals("Подписка на журнал", repo.onExpenseSaved(spent(null, "подписка на журнал", today, food), "Продукты", zone)?.title)
        assertTrue(db.recurring().getAll().isEmpty())
    }

    @Test
    fun sameDictationTwiceDoesNotDuplicateAndDismissedStaysDismissed() = runTest {
        savedAndAccepted(spent("Клод", null, today), "Связь и подписки")
        assertEquals(null, repo.onExpenseSaved(spent("Клод", null, today), "Связь и подписки", zone))
        assertEquals(1, db.recurring().getAll().size)

        repo.remove(db.recurring().getAll().single())
        assertEquals(null, repo.onExpenseSaved(spent("Клод", null, LocalDate.of(2026, 12, 14)), "Связь и подписки", zone))
        assertEquals(RecurringState.DISMISSED, db.recurring().getAll().single().state)
    }

    @Test
    fun nextMonthsDictationMovesTheDateInsteadOfAddingAPayment() = runTest {
        savedAndAccepted(spent("Клод", null, today), "Связь и подписки")
        // через месяц снова диктуют «Клод»: платёж один, дата уехала на следующий период, спрашивать не о чем
        assertEquals(null, repo.onExpenseSaved(spent("Клод", null, LocalDate.of(2026, 12, 14)), "Связь и подписки", zone))
        val saved = db.recurring().getAll().single()
        assertEquals(LocalDate.of(2027, 1, 14).toEpochDay(), saved.nextEpochDay)
    }

    @Test
    fun phraseWithoutPlaceAndNoteIsNamedBySubcategoryOrCategory() = runTest {
        // «мобильная связь 1200» и «подписка 2100»: ни места, ни заметки, только категория и подкатегория
        assertEquals("Мобильная связь", savedAndAccepted(spent(null, null, today, kopecks = 120_000), "Связь и подписки", "Мобильная связь"))
        assertEquals("Подписки", savedAndAccepted(spent(null, null, today, kopecks = 210_000), "Связь и подписки", "Подписки"))
        assertEquals(setOf("Мобильная связь", "Подписки"), db.recurring().getAll().map { it.title }.toSet())

        // без подкатегории — по названию категории
        val housing = db.categories().getAllWithSubs().single { it.category.name == "Жильё" }.category.id
        assertEquals("Жильё", repo.onExpenseSaved(spent(null, null, today, housing), "Жильё", zone)?.title)
    }

    @Test
    fun repeatedPhraseWithoutPlaceDoesNotDuplicate() = runTest {
        savedAndAccepted(spent(null, null, today, kopecks = 120_000), "Связь и подписки", "Мобильная связь")
        assertEquals(
            null,
            repo.onExpenseSaved(spent(null, null, LocalDate.of(2026, 12, 14), kopecks = 125_000), "Связь и подписки", zone, "Мобильная связь"),
        )
        val saved = db.recurring().getAll().single()
        assertEquals(125_000L, saved.amountKopecks)
        assertEquals(LocalDate.of(2027, 1, 14).toEpochDay(), saved.nextEpochDay)
    }

    @Test
    fun generatedAndReconcileExpensesAreIgnored() = runTest {
        assertEquals(null, repo.onExpenseSaved(spent("Клод", null, today, source = ExpenseSource.RECURRING), "Связь и подписки", zone))
        assertEquals(null, repo.onExpenseSaved(spent("Неучтённые", null, today, source = ExpenseSource.RECONCILE), "Связь и подписки", zone))
        assertTrue(db.recurring().getAll().isEmpty())
    }

    @Test
    fun utilitiesAreMarkedAsVaryingAndOthersAreNot() = runTest {
        val utilities = db.categories().getAllWithSubs().single { it.category.name == "Коммунальные услуги" }.category.id
        assertTrue(requireNotNull(repo.onExpenseSaved(spent(null, "квартплата", today, utilities, 650_000), "Коммунальные услуги", zone)).amountVaries)
        assertEquals(false, requireNotNull(repo.onExpenseSaved(spent("Клод", null, today), "Связь и подписки", zone)).amountVaries)
    }

    @Test
    fun nextMonthsUtilityBillUpdatesTheEstimate() = runTest {
        val utilities = db.categories().getAllWithSubs().single { it.category.name == "Коммунальные услуги" }.category.id
        savedAndAccepted(spent(null, "квартплата", today, utilities, 650_000), "Коммунальные услуги")
        repo.onExpenseSaved(spent(null, "квартплата", LocalDate.of(2026, 12, 14), utilities, 710_000), "Коммунальные услуги", zone)
        val saved = db.recurring().getAll().single()
        assertEquals(710_000L, saved.amountKopecks)
        assertEquals(LocalDate.of(2027, 1, 14).toEpochDay(), saved.nextEpochDay)
    }

    @Test
    fun markPaidWithActualAmountRemembersIt() = runTest {
        val id = repo.save(
            Recurring(
                title = "Квартплата", amountKopecks = 650_000, categoryId = categoryId, periodUnit = "month",
                nextEpochDay = today.toEpochDay(), amountVaries = true,
            )
        )
        assertTrue(repo.markPaid(id, today, zone, amountKopecks = 702_550))
        assertEquals(702_550L, db.expenses().getAllWithCategory().single().expense.amountKopecks)
        assertEquals(702_550L, db.recurring().getById(id)!!.amountKopecks)
    }

    @Test
    fun mergingCategoriesMovesPayments() = runTest {
        val categories = CategoryRepository(db)
        val other = db.categories().getAllWithSubs().first { it.category.name == "Развлечения" }.category.id
        repo.save(
            Recurring(
                title = "Кино", amountKopecks = 10_000, categoryId = other, periodUnit = "month",
                nextEpochDay = today.toEpochDay(),
            )
        )
        categories.merge(other, categoryId)
        assertEquals(categoryId, db.recurring().getAll().single().categoryId)
    }
}
