package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.ZoneOffset

class SearchElsewhereTest {
    private val zone = ZoneOffset.UTC

    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun expense(id: Long, date: String, note: String, kopecks: Long = 1_000_00) = ExpenseWithCategory(
        expense = Expense(id = id, amountKopecks = kopecks, categoryId = 1, timestamp = at(date), note = note, place = "Ozon"),
        categoryName = "Маркетплейсы",
        categoryEmoji = "📦",
        subcategoryName = "Ozon",
    )

    // Аванс за холодильник в мае, долг закрыт в июле, сейчас открыт ноябрь
    private val advance = expense(1, "2026-05-20", "аванс за холодильник", 15_000_00)
    private val debt = expense(2, "2026-07-03", "закрываю долг по холодильнику", 20_000_00)
    private val coffee = expense(3, "2026-07-04", "кофе")
    private val novemberFridge = expense(4, "2026-11-02", "полка в холодильник")
    private val all = listOf(advance, debt, coffee, novemberFridge)
    private val november = BillingPeriod.containing(LocalDate.parse("2026-11-15"), 1)

    @Test
    fun `находит по всем прошлым периодам, новые сверху, без открытого периода`() {
        val found = SearchElsewhere.find(all, emptyList(), "траты на холодильник", november, 1, zone)
        assertEquals(listOf(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-05-01")), found.map { it.period.startDate })
        assertEquals(listOf(2L), found[0].expenses.map { it.expense.id })
        assertEquals(20_000_00L, found[0].expenseTotal)
        assertEquals(listOf(1L), found[1].expenses.map { it.expense.id })
    }

    @Test
    fun `озон по-русски находит запись с Ozon в другом периоде`() {
        val found = SearchElsewhere.find(all, emptyList(), "холодильник озон", november, 1, zone)
        assertEquals(2, found.size)
    }

    @Test
    fun `периоды считаются от дня начала из настроек`() {
        // При дне начала 10 записи 3 и 4 июля относятся к периоду 10 июня — 9 июля
        val current = BillingPeriod.containing(LocalDate.parse("2026-11-15"), 10)
        val found = SearchElsewhere.find(all, emptyList(), "холодильник", current, 10, zone)
        assertEquals(
            listOf(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-06-10"), LocalDate.parse("2026-05-10")),
            found.map { it.period.startDate },
        )
    }

    @Test
    fun `доходы тоже ищутся, пустой запрос ничего не ищет`() {
        val refund = Income(id = 9, amountKopecks = 500_00, source = "Возврат", note = "возврат за холодильник", timestamp = at("2026-08-01"))
        val found = SearchElsewhere.find(emptyList(), listOf(refund), "холодильнику", november, 1, zone)
        assertEquals(listOf(9L), found.single().incomes.map { it.id })
        assertEquals(emptyList<PeriodMatches>(), SearchElsewhere.find(all, listOf(refund), "  ", november, 1, zone))
    }
}
