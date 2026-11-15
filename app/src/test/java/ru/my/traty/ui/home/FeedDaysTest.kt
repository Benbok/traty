package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FeedDaysTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(day: LocalDate, hour: Int) =
        day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private val d13 = LocalDate.of(2026, 11, 13)
    private val d14 = LocalDate.of(2026, 11, 14)

    @Test
    fun headerGoesOnFirstEntryOfEachDayWithExpenseTotal() {
        val feed = listOf(
            DayEntry("a", at(d14, 20), 10_000),
            DayEntry("b", at(d14, 9), 5_000),
            DayEntry("c", at(d13, 23), 7_000),
            DayEntry("d", at(d13, 8), 0), // доход
        )
        val h = dayHeaders(feed, zone)
        assertEquals(setOf("a", "c"), h.keys)
        assertEquals(DayHeader(d14, 15_000), h["a"])
        assertEquals(DayHeader(d13, 7_000), h["c"])
        assertNull(h["b"])
    }

    @Test
    fun emptyFeedHasNoHeaders() {
        assertEquals(emptyMap<String, DayHeader>(), dayHeaders(emptyList(), zone))
    }

    @Test
    fun nearMidnightBelongsToLocalDay() {
        val feed = listOf(DayEntry("x", at(d14, 0) + 60_000, 100))
        assertEquals(d14, dayHeaders(feed, zone)["x"]?.date)
    }

    @Test
    fun titles() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals("Сегодня", dayTitle(today, today))
        assertEquals("Вчера", dayTitle(d14, today))
        assertEquals("13 ноября, пт", dayTitle(d13, today))
    }
}
