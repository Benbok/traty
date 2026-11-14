package ru.my.traty.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.charts.model.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId

class ChartRangesTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun customRangeIncludesBothEndDates() {
        val r = customChartRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 9), zone)
        assertEquals(LocalDate.of(2026, 10, 12), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), r.endDate)
        assertEquals(millis(LocalDate.of(2026, 10, 12)), r.startMillis)
        // «по 9 ноября» включительно: верхняя граница — начало 10 ноября
        assertEquals(millis(LocalDate.of(2026, 11, 10)), r.endMillis)
    }

    @Test
    fun customRangeSwapsReversedDates() {
        val r = customChartRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 12), zone)
        assertEquals(LocalDate.of(2026, 10, 12), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), r.endDate)
    }

    @Test
    fun singleDayRangeCoversExactlyThatDay() {
        val day = LocalDate.of(2026, 11, 5)
        val r = customChartRange(day, day, zone)
        assertEquals(millis(day), r.startMillis)
        assertEquals(millis(day.plusDays(1)), r.endMillis)
    }

    @Test
    fun dailyPointsOnlyForMonthAndShortCustomRanges() {
        val start = LocalDate.of(2026, 1, 1)
        val short = customChartRange(start, start.plusDays(61), zone) // 62 дня
        val long = customChartRange(start, start.plusDays(62), zone) // 63 дня
        assertTrue(usesDailyPoints(ChartPeriod.CUSTOM, short))
        assertFalse(usesDailyPoints(ChartPeriod.CUSTOM, long))
        assertTrue(usesDailyPoints(ChartPeriod.MONTH, long))
        assertFalse(usesDailyPoints(ChartPeriod.YEAR, short))
        assertFalse(usesDailyPoints(ChartPeriod.ALL, short))
    }

    // ---- «3 мес» / «6 мес» / «Год» считаются от дня начала расчётного периода ----

    private fun window(today: LocalDate, period: ChartPeriod, startDay: Int): ChartRange =
        billingPeriodsRange(BillingPeriod.containing(today, startDay), period.billingPeriodCount()!!, startDay, zone)

    @Test
    fun quarterStartsAtBillingDayTwoPeriodsBack() {
        // день начала 10, сегодня 14 ноября: текущий период 10 ноя — 9 дек
        val r = window(LocalDate.of(2026, 11, 14), ChartPeriod.QUARTER, 10)
        assertEquals(LocalDate.of(2026, 9, 10), r.startDate)
        assertEquals(LocalDate.of(2026, 12, 9), r.endDate)
        assertEquals(millis(LocalDate.of(2026, 9, 10)), r.startMillis)
        assertEquals(millis(LocalDate.of(2026, 12, 10)), r.endMillis)
    }

    @Test
    fun quarterBeforeBillingDayUsesPreviousPeriodAsCurrent() {
        // 5 ноября при дне начала 10 ещё идёт период 10 окт — 9 ноя
        val r = window(LocalDate.of(2026, 11, 5), ChartPeriod.QUARTER, 10)
        assertEquals(LocalDate.of(2026, 8, 10), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 9), r.endDate)
    }

    @Test
    fun startDayOneKeepsCalendarMonths() {
        val r = window(LocalDate.of(2026, 11, 14), ChartPeriod.QUARTER, 1)
        assertEquals(LocalDate.of(2026, 9, 1), r.startDate)
        assertEquals(LocalDate.of(2026, 11, 30), r.endDate)
        val y = window(LocalDate.of(2026, 11, 14), ChartPeriod.YEAR, 1)
        assertEquals(LocalDate.of(2025, 12, 1), y.startDate)
        assertEquals(LocalDate.of(2026, 11, 30), y.endDate)
    }

    @Test
    fun quarterCrossesYearBoundary() {
        // 20 января 2027, день начала 15: периоды 15 ноя, 15 дек, 15 янв
        val r = window(LocalDate.of(2027, 1, 20), ChartPeriod.QUARTER, 15)
        assertEquals(LocalDate.of(2026, 11, 15), r.startDate)
        assertEquals(LocalDate.of(2027, 2, 14), r.endDate)
        // 3 января 2027 — ещё период 15 дек — 14 янв
        val early = window(LocalDate.of(2027, 1, 3), ChartPeriod.QUARTER, 15)
        assertEquals(LocalDate.of(2026, 10, 15), early.startDate)
        assertEquals(LocalDate.of(2027, 1, 14), early.endDate)
    }

    @Test
    fun halfYearAndYearUseBillingDay() {
        val today = LocalDate.of(2026, 11, 14)
        val h = window(today, ChartPeriod.HALF_YEAR, 25)
        // текущий период 25 окт — 24 ноя, плюс 5 предыдущих
        assertEquals(LocalDate.of(2026, 5, 25), h.startDate)
        assertEquals(LocalDate.of(2026, 11, 24), h.endDate)
        val y = window(today, ChartPeriod.YEAR, 25)
        assertEquals(LocalDate.of(2025, 11, 25), y.startDate)
        assertEquals(LocalDate.of(2026, 11, 24), y.endDate)
    }

    @Test
    fun lateStartDayAcrossFebruaryAndLeapYear() {
        // 28 — максимальный день начала: все месяцы, включая февраль, его содержат
        val r = window(LocalDate.of(2028, 3, 1), ChartPeriod.QUARTER, 28)
        // текущий: 28 фев — 27 мар 2028 (високосный год)
        assertEquals(LocalDate.of(2027, 12, 28), r.startDate)
        assertEquals(LocalDate.of(2028, 3, 27), r.endDate)
        val feb = window(LocalDate.of(2027, 2, 27), ChartPeriod.QUARTER, 28)
        assertEquals(LocalDate.of(2026, 11, 28), feb.startDate)
        assertEquals(LocalDate.of(2027, 2, 27), feb.endDate)
    }

    @Test
    fun startDayAbove28IsClampedTo28() {
        // в настройках выше 28 не выставить, но если значение 29–31 всё же придёт — считаем как 28
        for (day in 29..31) {
            assertEquals(
                window(LocalDate.of(2026, 3, 1), ChartPeriod.QUARTER, 28),
                window(LocalDate.of(2026, 3, 1), ChartPeriod.QUARTER, day),
            )
        }
    }

    @Test
    fun windowIsContiguousAndMatchesMonthFilter() {
        val today = LocalDate.of(2026, 11, 14)
        val current = BillingPeriod.containing(today, 10)
        // «Месяц» и окно из одного периода совпадают
        val one = billingPeriodsRange(current, 1, 10, zone)
        assertEquals(current.startDate, one.startDate)
        assertEquals(current.endDate, one.endDate)
        // окно «3 мес» ровно покрывается тремя периодами без дыр
        val r = window(today, ChartPeriod.QUARTER, 10)
        val buckets = billingBuckets(r.startDate, r.endDate, 10)
        assertEquals(3, buckets.size)
        assertEquals(r.startDate, buckets.first().startDate)
        assertEquals(r.endDate, buckets.last().endDate)
        buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.endDate.plusDays(1), b.startDate) }
    }

    @Test
    fun bucketsFollowBillingPeriodsAndAreClippedToRange() {
        val b = billingBuckets(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 14), 10)
        assertEquals(
            listOf(
                BillingPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 9)),
                BillingPeriod(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 9)),
                BillingPeriod(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 9)),
                BillingPeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 14)),
            ),
            b,
        )
        assertTrue(billingBuckets(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 10).isEmpty())
    }

    @Test
    fun bucketLabels() {
        val calendar = BillingPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        // короткое имя месяца зависит от локали JVM/Android («Окт» или «Окт.»)
        assertTrue(bucketShortLabel(calendar).startsWith("Окт"))
        assertEquals("Октябрь 2026", bucketFullLabel(calendar))
        val shifted = BillingPeriod(LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 9))
        assertTrue(bucketShortLabel(shifted).startsWith("Дек"))
        assertEquals("10 дек — 9 янв 2027", bucketFullLabel(shifted))
    }
}
