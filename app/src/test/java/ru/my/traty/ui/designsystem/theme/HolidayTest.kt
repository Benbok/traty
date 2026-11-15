package ru.my.traty.ui.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HolidayTest {
    private fun on(y: Int, m: Int, d: Int) = holidayOn(LocalDate.of(y, m, d))

    @Test
    fun newYearSpansTurnOfYear() {
        assertNull(on(2026, 12, 24))
        assertEquals(Holiday.NEW_YEAR, on(2026, 12, 25))
        assertEquals(Holiday.NEW_YEAR, on(2026, 12, 31))
        assertEquals(Holiday.NEW_YEAR, on(2027, 1, 1))
        assertEquals(Holiday.NEW_YEAR, on(2027, 1, 8))
        assertNull(on(2027, 1, 9))
    }

    @Test
    fun singleDayHolidays() {
        assertEquals(Holiday.DEFENDERS_DAY, on(2027, 2, 23))
        assertEquals(Holiday.WOMENS_DAY, on(2027, 3, 8))
        assertEquals(Holiday.SPRING_DAY, on(2027, 5, 1))
        assertEquals(Holiday.VICTORY_DAY, on(2027, 5, 9))
        assertNull(on(2027, 3, 7))
        assertNull(on(2027, 5, 10))
    }

    @Test
    fun ordinaryDaysHaveNoDecor() {
        assertNull(on(2026, 11, 15))
        assertNull(on(2028, 2, 29))
        assertNull(on(2027, 6, 12))
    }
}
