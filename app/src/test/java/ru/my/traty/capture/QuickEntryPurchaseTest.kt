package ru.my.traty.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.ExpenseSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Голос → трата → вопрос «Покупка частями?» / «Доплата?» → покупка. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class QuickEntryPurchaseTest {
    private lateinit var app: TratyApp
    private lateinit var db: AppDatabase
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.now(zone)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = app.container.db
    }

    private fun row(amount: String, note: String, place: String, terms: PurchaseTerms?, category: String = "Маркетплейсы") = EntryRow(
        type = EntryType.EXPENSE, amountText = amount, category = category, subcategory = place,
        place = place, note = note, source = "", dateText = today.toString(), purchase = terms,
    )

    private fun draft(vararg rows: EntryRow) = EntryDraft(rows.toList(), "фраза", EntryOrigin.VOICE)

    @Test
    fun advanceThenTopUpLinkToOnePurchase() = runTest {
        app.container.categories.seedIfEmpty()
        val qe = QuickEntry(db, app.container.categories, app.container.settings, zone) { null }

        qe.save(draft(row("20000", "холодильник", "Ozon", PurchaseTerms(kind = PurchaseTerms.PARTIAL))))
        val first = qe.purchaseOffers.pending.value.single()
        assertNull(first.match)
        assertTrue(qe.purchaseOffers.create(first, PurchaseForm.from(first).copy(total = "60000")))

        qe.save(draft(row("5000", "холодильник", "Ozon", PurchaseTerms(kind = PurchaseTerms.TOPUP))))
        val second = qe.purchaseOffers.pending.value.single()
        assertEquals("Холодильник", second.match?.title)
        assertTrue(qe.purchaseOffers.attach(second))

        val expenses = db.expenses().getAllWithCategory()
        assertEquals(2, expenses.size)
        assertTrue(expenses.all { it.expense.source == ExpenseSource.VOICE })
        val debt = db.debts().getActive().single()
        assertEquals(2_500_000L, db.debts().getPayments(debt.id).sumOf { it.amountKopecks })
        assertEquals(6_000_000L, debt.totalKopecks)
    }

    @Test
    fun loanPaymentIsNotOfferedAsSubscription() = runTest {
        app.container.categories.seedIfEmpty()
        val qe = QuickEntry(db, app.container.categories, app.container.settings, zone) { null }
        val terms = PurchaseTerms(kind = PurchaseTerms.LOAN, total = 80000.0, count = 12, rate = 20.0)
        val result = qe.save(draft(row("7410.76", "ноутбук", "", terms, category = "Долги и кредиты")))
        assertTrue(result.recurringOffers.isEmpty())
        assertEquals(1, qe.purchaseOffers.pending.value.size)
    }

    @Test
    fun unpaidLoanCreatesNoExpenseButOffersPurchase() = runTest {
        app.container.categories.seedIfEmpty()
        val qe = QuickEntry(db, app.container.categories, app.container.settings, zone) { null }
        val terms = PurchaseTerms(kind = PurchaseTerms.LOAN, total = 80000.0, count = 12, rate = 20.0)
        qe.save(draft(row("0", "ноутбук", "", terms, category = "Дом и быт")))
        assertTrue(db.expenses().getAllWithCategory().isEmpty())
        val offer = qe.purchaseOffers.pending.value.single()
        assertNull(offer.expenseId)
        assertTrue(qe.purchaseOffers.create(offer, PurchaseForm.from(offer)))
        assertEquals(1, db.debts().getActive().size)
    }
}
