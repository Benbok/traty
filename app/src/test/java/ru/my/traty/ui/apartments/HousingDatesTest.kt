package ru.my.traty.ui.apartments

import org.junit.Assert.*
import org.junit.Test
import ru.my.traty.data.apartments.ReadingDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

class HousingDatesTest {
    @Test
    fun dayMonthYearIsUnambiguousAndKeepsStoredDate() {
        val date = LocalDate.of(2026, 4, 3)
        assertEquals("03.04.2026", HousingDates.format(ReadingDates.encode(date)))
        assertEquals(date, HousingDates.parse(" 03.04.2026 "))
        assertEquals(LocalDate.of(2024, 2, 29), HousingDates.parse("29.02.2024"))
    }

    @Test
    fun invalidDatesAreRejectedInsteadOfSilentlyChangingDay() {
        listOf("29.02.2026", "31.04.2026", "00.11.2026", "17.13.2026", "2026-11-17").forEach {
            assertThrows(it, DateTimeParseException::class.java) { HousingDates.parse(it) }
        }
    }

    @Test
    fun submissionMonthUsesMonthBeforeYear() {
        assertEquals("11.2026", HousingDates.formatMonth(202611))
        assertEquals(YearMonth.of(2026, 11), HousingDates.parseMonth("11.2026"))
        assertThrows(DateTimeParseException::class.java) { HousingDates.parseMonth("13.2026") }
    }
}
