package ru.my.traty.data.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DailyDigestScheduleTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val now = Instant.parse("2026-11-19T15:00:00Z")

    @Test
    fun defaultTimeIsEightInTheEveningLocally() {
        val slot = nextDigestSlot(now, moscow, DEFAULT_DIGEST_TIME)
        assertEquals(Instant.parse("2026-11-19T17:00:00Z"), slot.at)
    }

    @Test
    fun newTimeThatAlreadyPassedStartsTomorrow() {
        val slot = nextDigestSlot(now, moscow, LocalTime.of(16, 30))
        assertEquals(Instant.parse("2026-11-20T13:30:00Z"), slot.at)
    }

    @Test
    fun laterTimeCanStillBeScheduledToday() {
        val slot = nextDigestSlot(now, moscow, LocalTime.of(22, 45))
        assertEquals(Instant.parse("2026-11-19T19:45:00Z"), slot.at)
    }

    @Test
    fun changingTimeAfterSendingSchedulesTomorrow() {
        val slot = nextDigestSlot(now, moscow, LocalTime.of(22, 45), LocalDate.of(2026, 11, 19))
        assertEquals(LocalDate.of(2026, 11, 20), slot.date)
    }

    @Test
    fun changingTimeZoneRecalculatesTheNextLocalSlot() {
        val slot = nextDigestSlot(now, ZoneId.of("Asia/Yekaterinburg"), DEFAULT_DIGEST_TIME)
        assertEquals(Instant.parse("2026-11-20T15:00:00Z"), slot.at)
    }

    @Test
    fun delayedWorkCanSendOnlyForItsOriginalConfigurationAndDate() {
        val slot = nextDigestSlot(now, moscow, DEFAULT_DIGEST_TIME)
        val delayed = Instant.parse("2026-11-19T17:41:00Z")
        assertTrue(slot.canSend(delayed, moscow, DEFAULT_DIGEST_TIME, null))
        assertFalse(slot.canSend(delayed, moscow, DEFAULT_DIGEST_TIME, slot.date))
        assertFalse(slot.canSend(delayed, moscow, LocalTime.of(19, 0), null))
        assertFalse(slot.canSend(delayed, ZoneId.of("Asia/Yekaterinburg"), DEFAULT_DIGEST_TIME, null))
        assertFalse(slot.canSend(delayed.plusSeconds(86_400), moscow, DEFAULT_DIGEST_TIME, null))
    }
}
