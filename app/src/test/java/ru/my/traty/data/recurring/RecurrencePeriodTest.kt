package ru.my.traty.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrencePeriodTest {
    private val d = LocalDate.of(2026, 1, 31)

    @Test
    fun advanceMovesByCalendarUnits() {
        assertEquals(LocalDate.of(2026, 2, 7), RecurrencePeriod.WEEKLY.advance(d))
        assertEquals(LocalDate.of(2026, 2, 28), RecurrencePeriod.MONTHLY.advance(d))
        assertEquals(LocalDate.of(2027, 1, 31), RecurrencePeriod.YEARLY.advance(d))
        assertEquals(LocalDate.of(2026, 3, 17), RecurrencePeriod(PeriodUnit.DAY, 45).advance(d))
        assertEquals(LocalDate.of(2026, 4, 30), RecurrencePeriod(PeriodUnit.MONTH, 3).advance(d))
    }

    @Test
    fun nextOnOrAfterSkipsMissedPeriods() {
        val today = LocalDate.of(2026, 5, 10)
        assertEquals(LocalDate.of(2026, 5, 15), RecurrencePeriod.MONTHLY.nextOnOrAfter(LocalDate.of(2026, 2, 15), today))
        assertEquals(today, RecurrencePeriod.MONTHLY.nextOnOrAfter(today, today))
        assertEquals(LocalDate.of(2026, 6, 1), RecurrencePeriod.MONTHLY.nextOnOrAfter(LocalDate.of(2026, 6, 1), today))
    }

    @Test
    fun costsPerMonthAndYear() {
        // 399 ₽ в месяц
        assertEquals(39_900L, RecurrencePeriod.MONTHLY.monthlyKopecks(39_900))
        assertEquals(478_800L, RecurrencePeriod.MONTHLY.yearlyKopecks(39_900))
        // 2 400 ₽ в год → 200 ₽ в месяц
        assertEquals(20_000L, RecurrencePeriod.YEARLY.monthlyKopecks(240_000))
        // раз в 3 месяца по 1 200 ₽ → 4 800 ₽ в год
        assertEquals(480_000L, RecurrencePeriod(PeriodUnit.MONTH, 3).yearlyKopecks(120_000))
        // 100 ₽ в неделю → 365/7 платежей в год
        assertEquals(521_428L, RecurrencePeriod.WEEKLY.yearlyKopecks(10_000))
    }

    @Test
    fun labelsAreShort() {
        assertEquals("в мес.", RecurrencePeriod.MONTHLY.label())
        assertEquals("раз в 45 дн.", RecurrencePeriod(PeriodUnit.DAY, 45).label())
    }

    @Test
    fun unknownUnitKeyReadsAsMonth() {
        assertEquals(PeriodUnit.MONTH, PeriodUnit.of("garbage"))
        assertEquals(PeriodUnit.YEAR, PeriodUnit.of("year"))
    }
}
