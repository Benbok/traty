package ru.my.traty.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import java.time.LocalDate
import java.time.ZoneId

class RecurringDetectorTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun exp(
        date: LocalDate,
        kopecks: Long,
        place: String? = "Яндекс Плюс",
        note: String? = null,
        categoryId: Long = 8,
        source: String = ExpenseSource.VOICE,
    ) = ExpenseWithCategory(
        expense = Expense(
            id = nextId++, amountKopecks = kopecks, categoryId = categoryId, place = place, note = note,
            timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), source = source,
        ),
        categoryName = "Связь и подписки", categoryEmoji = "📱", subcategoryName = null,
    )

    private fun detect(
        items: List<ExpenseWithCategory>,
        excluded: Set<String> = emptySet(),
        relaxed: Set<Long> = emptySet(),
    ) = RecurringDetector.detect(items, today, zone, excluded, relaxed)

    private fun monthly(n: Int, kopecks: Long = 39_900, place: String? = "Яндекс Плюс", day: Int = 5, lastMonth: Int = 11) =
        (0 until n).map { exp(LocalDate.of(2026, lastMonth - (n - 1 - it), day), kopecks, place) }

    @Test
    fun threeMonthlyChargesAreASubscription() {
        val c = detect(monthly(3)).single()
        assertEquals("Яндекс Плюс", c.title)
        assertEquals(RecurrencePeriod.MONTHLY, c.period)
        assertEquals(39_900L, c.amountKopecks)
        assertEquals(3, c.charges)
        assertEquals(LocalDate.of(2026, 11, 5), c.lastDate)
        assertEquals(LocalDate.of(2026, 12, 5), c.nextDate)
        assertEquals("8|яндекс плюс", c.key)
    }

    @Test
    fun twoMonthlyChargesAreNotEnoughUnlessCategoryIsRelaxed() {
        assertTrue(detect(monthly(2)).isEmpty())
        assertEquals(1, detect(monthly(2), relaxed = setOf(8L)).size)
    }

    @Test
    fun amountsWithinTenPercentAreOneSubscriptionAndLatestPriceWins() {
        val items = listOf(
            exp(LocalDate.of(2026, 9, 5), 36_900), exp(LocalDate.of(2026, 10, 5), 39_000), exp(LocalDate.of(2026, 11, 5), 39_900),
        )
        assertEquals(39_900L, detect(items).single().amountKopecks)
    }

    @Test
    fun amountsFarApartAreNotMerged() {
        val items = listOf(
            exp(LocalDate.of(2026, 9, 5), 39_900), exp(LocalDate.of(2026, 10, 5), 99_900), exp(LocalDate.of(2026, 11, 5), 39_900),
        )
        // две по 399 с интервалом 61 день — не месяц; одна по 999 — мало
        assertTrue(detect(items).isEmpty())
    }

    @Test
    fun subscriptionIsFoundAmongOtherPurchasesAtTheSamePlace() {
        val items = monthly(3) + listOf(exp(LocalDate.of(2026, 10, 20), 150_000), exp(LocalDate.of(2026, 11, 2), 7_000))
        val c = detect(items).single()
        assertEquals(39_900L, c.amountKopecks)
    }

    @Test
    fun irregularIntervalsAreNotASubscription() {
        val items = listOf(
            exp(LocalDate.of(2026, 8, 5), 39_900), exp(LocalDate.of(2026, 9, 19), 39_900), exp(LocalDate.of(2026, 11, 5), 39_900),
        )
        assertTrue(detect(items).isEmpty())
    }

    @Test
    fun weeklyNeedsFourChargesAndYearlyNeedsTwo() {
        fun weekly(n: Int) = (0 until n).map { exp(today.minusDays(7L * (n - 1 - it) + 2), 50_000, "Бассейн", categoryId = 10) }
        assertTrue(detect(weekly(3)).isEmpty())
        assertEquals(RecurrencePeriod.WEEKLY, detect(weekly(4)).single().period)

        val yearly = listOf(exp(LocalDate.of(2025, 11, 1), 240_000, "Домен"), exp(LocalDate.of(2024, 11, 1), 240_000, "Домен"))
        // 2024-11-01 → 2025-11-01 = 365 дней
        val y = detect(yearly).single()
        assertEquals(RecurrencePeriod.YEARLY, y.period)
        assertEquals(LocalDate.of(2026, 11, 1), y.nextDate)
    }

    @Test
    fun staleSubscriptionIsDropped() {
        // последнее списание полгода назад
        val old = (0 until 3).map { exp(LocalDate.of(2026, 3 - it, 5), 39_900) }
        assertTrue(detect(old).isEmpty())
        // последнее списание 40 дней назад — ещё актуально
        val recent = (0 until 3).map { exp(LocalDate.of(2026, 10, 5).minusMonths(it.toLong()), 39_900) }
        assertEquals(1, detect(recent).size)
    }

    @Test
    fun placeSpellingsAreOneSubscription() {
        val items = listOf(
            exp(LocalDate.of(2026, 9, 5), 39_900, "Яндекс Плюс"),
            exp(LocalDate.of(2026, 10, 5), 39_900, "яндекс плюс"),
            exp(LocalDate.of(2026, 11, 5), 39_900, "\"Яндекс  плюс\""),
        )
        assertEquals(3, detect(items).single().charges)
    }

    @Test
    fun noteIsUsedWhenThereIsNoPlace() {
        val items = (0 until 3).map { exp(LocalDate.of(2026, 9 + it, 5), 39_900, place = null, note = "Облако") }
        assertEquals("Облако", detect(items).single().title)
        assertEquals("8|облако", detect(items).single().key)
    }

    @Test
    fun withoutPlaceAndNoteNothingIsGrouped() {
        val items = (0 until 3).map { exp(LocalDate.of(2026, 9 + it, 5), 39_900, place = null) }
        assertTrue(detect(items).isEmpty())
        assertNull(RecurringDetector.keyOf(8, null, " "))
    }

    @Test
    fun excludedKeysAreNotSuggestedAgain() {
        assertTrue(detect(monthly(3), excluded = setOf("8|яндекс плюс")).isEmpty())
    }

    @Test
    fun reconcileEntriesAreIgnored() {
        val items = (0 until 3).map { exp(LocalDate.of(2026, 9 + it, 5), 39_900, "Неучтённые", source = ExpenseSource.RECONCILE) }
        assertTrue(detect(items).isEmpty())
    }

    @Test
    fun installmentPaymentsAreNotSubscriptions() {
        // Рассрочка: те же 10 000 ₽ каждый месяц, но у неё свой график в «Покупках частями»
        val items = (0 until 3).map { exp(LocalDate.of(2026, 9 + it, 5), 1_000_000, "Ozon", "Телевизор · платёж ${it + 1} из 6", source = ExpenseSource.DEBT) }
        assertTrue(detect(items).isEmpty())
    }

    @Test
    fun regularContributionsAreIgnored() {
        val items = (0 until 3).map {
            exp(LocalDate.of(2026, 9 + it, 5), 39_900, "Отложено на наушники", source = ExpenseSource.PURCHASE_CONTRIBUTION)
        }
        assertTrue(detect(items).isEmpty())
    }

    @Test
    fun twoPurchasesOnOneDayCountOnce() {
        val items = monthly(3) + exp(LocalDate.of(2026, 11, 5), 39_900)
        assertEquals(3, detect(items).single().charges)
    }

    @Test
    fun mostExpensiveSubscriptionComesFirst() {
        val items = monthly(3, 10_000, "Облако") + monthly(3, 99_900, "Кино")
        assertEquals(listOf("Кино", "Облако"), detect(items).map { it.title })
    }
}
