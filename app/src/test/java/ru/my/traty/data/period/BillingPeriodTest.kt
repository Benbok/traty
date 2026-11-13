package ru.my.traty.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillingPeriodTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun containingMiddleOfPeriod() {
        val p = BillingPeriod.containing(d(2026, 11, 15), 10)
        assertEquals(BillingPeriod(d(2026, 11, 10), d(2026, 12, 9)), p)
    }

    @Test
    fun dayOfMonthStartIsInclusive() {
        val p = BillingPeriod.containing(d(2026, 11, 10), 10)
        assertEquals(d(2026, 11, 10), p.startDate)
    }

    @Test
    fun dayBeforeStartBelongsToPreviousPeriod() {
        val p = BillingPeriod.containing(d(2026, 11, 9), 10)
        assertEquals(BillingPeriod(d(2026, 10, 10), d(2026, 11, 9)), p)
    }

    @Test
    fun startDayOneIsCalendarMonth() {
        val p = BillingPeriod.containing(d(2026, 2, 17), 1)
        assertEquals(BillingPeriod(d(2026, 2, 1), d(2026, 2, 28)), p)
    }

    @Test
    fun crossesYearBoundary() {
        val p = BillingPeriod.containing(d(2026, 12, 30), 28)
        assertEquals(BillingPeriod(d(2026, 12, 28), d(2027, 1, 27)), p)
    }

    @Test
    fun previousAndNext() {
        val p = BillingPeriod(d(2026, 11, 10), d(2026, 12, 9))
        val prev = p.previous(10)
        assertEquals(BillingPeriod(d(2026, 10, 10), d(2026, 11, 9)), prev)
        assertEquals(p, prev.next(10))
    }

    @Test
    fun contains() {
        val p = BillingPeriod(d(2026, 11, 10), d(2026, 12, 9))
        assertTrue(d(2026, 11, 10) in p)
        assertTrue(d(2026, 12, 9) in p)
        assertFalse(d(2026, 11, 9) in p)
        assertFalse(d(2026, 12, 10) in p)
    }

    @Test
    fun adjacentPeriodsHaveNoGap() {
        val zone = ZoneId.of("Europe/Moscow")
        var p = BillingPeriod.containing(d(2026, 12, 30), 28)
        repeat(14) {
            val n = p.next(28)
            assertEquals(p.endMillisExclusive(zone), n.startMillis(zone))
            p = n
        }
    }
}
