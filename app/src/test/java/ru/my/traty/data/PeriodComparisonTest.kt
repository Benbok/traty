package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

class PeriodComparisonTest {
    private fun t(id: Long, rub: Long, name: String = "c$id") = CategoryTotal(id, name, "", rub * 100)

    @Test
    fun changesInRublesAndPercent() {
        val ch = PeriodComparison.changes(
            current = listOf(t(1, 15_000, "Продукты"), t(2, 2_000, "Авто"), t(3, 500, "Кафе")),
            previous = listOf(t(1, 10_000, "Продукты"), t(2, 6_000, "Авто"), t(3, 500, "Кафе")),
        )
        // «Кафе» не изменилось и не показывается; порядок — по модулю разницы
        assertEquals(listOf("Продукты", "Авто"), ch.map { it.name })
        assertEquals(5_000_00L, ch[0].diff)
        assertEquals(50, ch[0].percent)
        assertEquals(-4_000_00L, ch[1].diff)
        assertEquals(-67, ch[1].percent)
    }

    @Test
    fun newAndGoneCategoriesAreChangesToo() {
        val ch = PeriodComparison.changes(current = listOf(t(1, 300, "Подарки")), previous = listOf(t(2, 1_000, "Кино")))
        assertEquals(listOf("Кино", "Подарки"), ch.map { it.name })
        assertEquals(-100, ch[0].percent)
        assertEquals(0L, ch[0].current)
        assertNull(ch[1].percent)
    }

    @Test
    fun topTakesThreeBiggest() {
        val top = PeriodComparison.top(
            current = listOf(t(1, 100), t(2, 200), t(3, 300), t(4, 400)),
            previous = emptyList(),
        )
        assertEquals(listOf(4L, 3L, 2L), top.map { it.categoryId })
    }

    private val period = BillingPeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 9))
    private val prev = BillingPeriod(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 9))

    @Test
    fun runningPeriodComparesSameNumberOfDays() {
        // Сегодня 5-й день периода → первые 5 дней прошлого: 10–14 октября
        assertEquals(
            LocalDate.of(2026, 10, 10) to LocalDate.of(2026, 10, 14),
            PeriodComparison.comparableWindow(period, prev, LocalDate.of(2026, 11, 14)),
        )
    }

    @Test
    fun windowNeverRunsPastPreviousPeriod() {
        // Февраль короче января: 30-й день текущего периода упирается в конец прошлого
        val feb = BillingPeriod(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28))
        val mar = BillingPeriod(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31))
        assertEquals(feb.startDate to feb.endDate, PeriodComparison.comparableWindow(mar, feb, LocalDate.of(2027, 3, 31)))
    }

    @Test
    fun finishedPeriodComparesWholePrevious() {
        assertEquals(
            prev.startDate to prev.endDate,
            PeriodComparison.comparableWindow(period, prev, LocalDate.of(2027, 1, 5)),
        )
    }
}
