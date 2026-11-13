package ru.my.traty.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

class FormatPeriodTest {
    @Test
    fun startDayOneShowsMonthTitle() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 11, 13), 1)
        assertEquals("Ноябрь 2026", formatPeriod(p, 1))
    }

    @Test
    fun customStartDayShowsRange() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 11, 13), 10)
        assertEquals("10 ноя — 9 дек", formatPeriod(p, 10))
    }

    @Test
    fun yearBoundaryShowsYear() {
        val p = BillingPeriod.containing(LocalDate.of(2026, 12, 20), 10)
        assertEquals("10 дек — 9 янв 2027", formatPeriod(p, 10))
    }
}
