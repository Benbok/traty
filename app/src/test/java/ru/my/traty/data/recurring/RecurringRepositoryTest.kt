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
