package ru.my.traty.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Единое правило времени пользовательской записи: сегодня - текущее время, любой другой день - 12:00.
 * Полночь нельзя: запись «сегодня» оказалась бы раньше якоря утренней сверки и не попала бы в «ожидаемое».
 */
fun entryTimestamp(
    day: LocalDate,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
    nowTime: LocalTime = LocalTime.now(zone),
): Long {
    val time = if (day == today) nowTime else LocalTime.NOON
    return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
