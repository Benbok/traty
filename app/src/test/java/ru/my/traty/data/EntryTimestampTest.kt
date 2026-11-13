package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EntryTimestampTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val now = LocalTime.of(15, 20)

    private fun millis(day: LocalDate, time: LocalTime, zone: ZoneId) =
        day.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun todayUsesGivenTime() {
        assertEquals(millis(today, now, ZoneOffset.UTC), entryTimestamp(today, ZoneOffset.UTC, today, now))
    }

    @Test
    fun yesterdayAndTomorrowUseNoon() {
        val y = today.minusDays(1)
        val t = today.plusDays(1)
        assertEquals(millis(y, LocalTime.NOON, ZoneOffset.UTC), entryTimestamp(y, ZoneOffset.UTC, today, now))
        assertEquals(millis(t, LocalTime.NOON, ZoneOffset.UTC), entryTimestamp(t, ZoneOffset.UTC, today, now))
    }

    @Test
    fun zoneIsRespected() {
        val plus3 = ZoneOffset.ofHours(3)
        val y = today.minusDays(1)
        assertEquals(millis(y, LocalTime.NOON, plus3), entryTimestamp(y, plus3, today, now))
        assertEquals(3 * 3_600_000L, entryTimestamp(y, ZoneOffset.UTC, today, now) - entryTimestamp(y, plus3, today, now))
    }
}
