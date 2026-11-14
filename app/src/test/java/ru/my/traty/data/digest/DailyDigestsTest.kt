package ru.my.traty.data.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import java.time.LocalDate
import java.time.ZoneOffset

class DailyDigestsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun exp(daysAgo: Int, rub: Long, cat: Long = 1, catName: String = "Продукты", source: String = ExpenseSource.VOICE) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = cat, source = source,
                timestamp = today.minusDays(daysAgo.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = catName, categoryEmoji = "🛒", subcategoryName = null,
        )

    /** 30 дней истории по 1000 ₽ в день: обычный день = 1000 ₽. */
    private fun history() = (1..30).map { exp(it, 1000) }

    private fun build(rows: List<ExpenseWithCategory>, incomes: List<Income> = emptyList()) =
        DailyDigests.build(rows, incomes, today, zone)

    @Test
    fun noExpensesAtAllMeansNothingToSay() {
        assertNull(build(emptyList()))
    }

    @Test
    fun lowerThanUsualIsPraised() {
        val d = build(history() + exp(0, 400))!!
        assertEquals(DigestTone.LOWER, d.tone)
        assertTrue(d.title.contains("меньше"))
        assertTrue("400 ₽" in d.body && "1" in d.body)
    }

    @Test
    fun closeToUsualIsCalm() {
        assertEquals(DigestTone.USUAL, build(history() + exp(0, 1100))!!.tone)
    }

    @Test
    fun higherThanUsualIsSupportive() {
        val d = build(history() + exp(0, 1500))!!
        assertEquals(DigestTone.HIGHER, d.tone)
        assertTrue("Бывает" in d.body)
    }

    @Test
    fun muchHigherSaysHowManyTimes() {
        val d = build(history() + exp(0, 3000))!!
        assertEquals(DigestTone.MUCH_HIGHER, d.tone)
        assertTrue("в 3 раза больше" in d.body)
    }

    @Test
    fun noSpendingTodayDoesNotAssumeTheBest() {
        val d = build(history())!!
        assertEquals(DigestTone.NO_SPENDING, d.tone)
        assertTrue("забыли записать" in d.body)
    }

    @Test
    fun shortHistoryOnlyReportsTheSum() {
        val rows = (1..5).map { exp(it, 1000) } + exp(0, 700)
        val d = build(rows)!!
        assertEquals(DigestTone.NO_BASELINE, d.tone)
        assertTrue("700 ₽" in d.title)
    }

    @Test
    fun oneExpensiveDayDoesNotMoveTheUsualDay() {
        val rows = history().map { it.expense } + Expense(amountKopecks = 5_000_000, categoryId = 1, timestamp = today.minusDays(3).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        // медиана остаётся 1000 ₽ даже с покупкой на 50 000 ₽
        assertEquals(100_000L, DailyDigests.usualDay(rows, today, zone))
    }

    @Test
    fun daysWithoutSpendingCountAsZeros() {
        // траты только каждый четвёртый день: в медиане сидят нули, обычный день слишком дёшев для сравнения
        val rows = (1..30 step 4).map { exp(it, 1000) } + exp(0, 500)
        assertNull(DailyDigests.usualDay(rows.map { it.expense }, today, zone))
        assertEquals(DigestTone.NO_BASELINE, build(rows)!!.tone)
    }

    @Test
    fun reconcileRecordsAreIgnored() {
        val rows = history() + exp(0, 400) + exp(0, 90_000, source = ExpenseSource.RECONCILE)
        val d = build(rows)!!
        assertEquals(DigestTone.LOWER, d.tone)
        assertTrue("90" !in d.body.replace("1 000", ""))
    }

    @Test
    fun incomeAndTopCategoryAreMentioned() {
        val rows = history() + exp(0, 300, cat = 1) + exp(0, 700, cat = 2, catName = "Кафе")
        val income = Income(amountKopecks = 5_000_000, timestamp = today.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val reconcileIncome = income.copy(amountKopecks = 1, origin = IncomeOrigin.RECONCILE)
        val d = build(rows, listOf(income, reconcileIncome))!!
        assertTrue("Кафе" in d.body)
        assertTrue("Поступило: 50" in d.body)
        assertTrue("в плюсе" in d.body)
    }

    @Test
    fun singleCategoryDoesNotRepeatItself() {
        val d = build(history() + exp(0, 400))!!
        assertTrue("Больше всего" !in d.body)
    }

    @Test
    fun pluralOfTimes() {
        for ((rub, word) in listOf(2000L to "раза", 5000L to "раз")) {
            assertNotNull(build(history() + exp(0, rub))!!.body.takeIf { "в ${rub / 1000} $word" in it })
        }
    }
}
