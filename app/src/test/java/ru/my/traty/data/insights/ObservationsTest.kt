package ru.my.traty.data.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import java.time.LocalDate
import java.time.ZoneOffset

class ObservationsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14) // 14-й день периода 1–30 ноября
    private var nextId = 1L

    private fun exp(date: LocalDate, rub: Long, cat: Long = 1, name: String = "Продукты", source: String = ExpenseSource.VOICE) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = cat, source = source,
                timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = name, categoryEmoji = "🛒", subcategoryName = null,
        )

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    /** По 1000 ₽ в день в октябре и в первые 14 дней ноября. */
    private fun steady(cat: Long = 1, name: String = "Продукты") =
        (1..31).map { exp(oct(it), 1000, cat, name) } + (1..14).map { exp(nov(it), 1000, cat, name) }

    private fun build(rows: List<ExpenseWithCategory>) = Observations.build(rows, today, 1, zone)

    @Test
    fun nothingToSayWithoutHistory() {
        assertTrue(build(emptyList()).isEmpty())
        // нет прошлого периода: сравнивать не с чем
        assertTrue(build((1..14).map { exp(nov(it), 1000) }).isEmpty())
    }

    @Test
    fun steadySpendingIsQuiet() {
        assertTrue(build(steady()).isEmpty())
    }

    @Test
    fun reconcileRecordsAreIgnored() {
        assertTrue(build(steady() + exp(nov(10), 500_000, source = ExpenseSource.RECONCILE)).isEmpty())
    }

    @Test
    fun forecastAboveLastPeriodWarnsAndGivesDailyLimit() {
        // в ноябре тратят по 2000 ₽ в день, в октябре было по 1000
        val rows = (1..31).map { exp(oct(it), 1000) } + (1..14).map { exp(nov(it), 2000) }
        val o = build(rows).single { it.kind == ObservationKind.FORECAST_HIGH }
        assertTrue("Чтобы уложиться" in o.text)
        assertEquals("forecast:2026-11-01", o.key)
    }

    @Test
    fun forecastBelowLastPeriodIsPraised() {
        val rows = (1..31).map { exp(oct(it), 1000) } + (1..14).map { exp(nov(it), 500) }
        assertTrue(build(rows).any { it.kind == ObservationKind.FORECAST_LOW })
    }

    @Test
    fun forecastWaitsForAWeekOfData() {
        val early = LocalDate.of(2026, 11, 5)
        val rows = (1..31).map { exp(oct(it), 1000) } + (1..5).map { exp(nov(it), 5000) }
        assertTrue(Observations.build(rows, early, 1, zone).none { it.kind == ObservationKind.FORECAST_HIGH })
    }

    @Test
    fun categoryGrowthIsReportedWithPercent() {
        val cafe = (1..14).map { exp(nov(it), 300, cat = 2, name = "Кафе") } // 4200 ₽ за 14 дней
        val oldCafe = (1..14).map { exp(oct(it), 100, cat = 2, name = "Кафе") } // 1400 ₽ за тот же срок
        val up = build(steady() + cafe + oldCafe).single { it.kind == ObservationKind.CATEGORY_UP }
        assertEquals("up:2:2026-11-01", up.key)
        assertTrue("200%" in up.text)
    }

    @Test
    fun newCategoryIsAnnounced() {
        val taxi = listOf(exp(nov(3), 1500, cat = 3, name = "Такси"))
        val up = build(steady() + taxi).single { it.kind == ObservationKind.CATEGORY_UP }
        assertTrue("Новая статья" in up.title)
    }

    @Test
    fun smallChangesAreIgnored() {
        val extra = (1..14).map { exp(nov(it), 30, cat = 2, name = "Кафе") } // 420 ₽ < 1000 ₽
        assertTrue(build(steady() + extra).none { it.kind == ObservationKind.CATEGORY_UP })
    }

    @Test
    fun categoryDropIsPraised() {
        val taxiOld = (1..14).map { exp(oct(it), 300, cat = 3, name = "Такси") } // 4200 ₽
        val taxiNow = listOf(exp(nov(2), 500, cat = 3, name = "Такси"))
        val down = build(steady() + taxiOld + taxiNow).single { it.kind == ObservationKind.CATEGORY_DOWN }
        assertTrue("Экономия" in down.text)
        assertTrue("Такси" in down.title)
    }

    @Test
    fun expensiveWeekdayIsFound() {
        // пятница (6 ноября и каждая неделя назад) стоит 5000 ₽, остальные дни по 500 ₽
        val rows = (0..90).map { n ->
            val d = today.minusDays(n.toLong())
            exp(d, if (d.dayOfWeek == java.time.DayOfWeek.FRIDAY) 5000 else 500)
        }
        val o = Observations.build(rows, today, 1, zone).single { it.kind == ObservationKind.WEEKDAY }
        assertTrue(o.title.startsWith("Пятница"))
    }

    @Test
    fun visibleHidesDismissedAndLimitsCount() {
        val all = (1..4).map { Observation("k$it", ObservationKind.WEEKDAY, "x", "t", "x") }
        assertEquals(listOf("k1", "k2"), Observations.visible(all, emptySet()).map { it.key })
        assertEquals(listOf("k2", "k3"), Observations.visible(all, setOf("k1")).map { it.key })
    }
}
