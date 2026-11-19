package ru.my.traty.data.digest

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal val DEFAULT_DIGEST_TIME: LocalTime = LocalTime.of(20, 0)

internal data class DigestSettings(val enabled: Boolean, val time: LocalTime)

internal data class DigestSlot(
    val date: LocalDate,
    val zone: ZoneId,
    val time: LocalTime,
) {
    val at: Instant get() = date.atTime(time).atZone(zone).toInstant()

    fun canSend(now: Instant, currentZone: ZoneId, currentTime: LocalTime, lastSent: LocalDate?): Boolean =
        zone == currentZone && time == currentTime && now.atZone(zone).toLocalDate() == date &&
            now >= at && lastSent != date
}

internal fun nextDigestSlot(
    now: Instant,
    zone: ZoneId,
    time: LocalTime,
    lastSent: LocalDate? = null,
): DigestSlot {
    var slot = DigestSlot(now.atZone(zone).toLocalDate(), zone, time)
    if (slot.date == lastSent || !slot.at.isAfter(now)) {
        slot = slot.copy(date = slot.date.plusDays(1))
    }
    return slot
}
