package ru.my.traty.ui.designsystem.theme

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

/**
 * Праздничное оформление: в эти дни меняется акцентный цвет и значок в шапке вкладок.
 * Выключается в «Настройки → Тема оформления».
 */
enum class Holiday(val emoji: String, val greeting: String) {
    NEW_YEAR("🎄", "С Новым годом!"),
    DEFENDERS_DAY("⭐", "С 23 Февраля!"),
    WOMENS_DAY("🌷", "С 8 Марта!"),
    SPRING_DAY("🌸", "С Праздником весны и труда!"),
    VICTORY_DAY("🕊️", "С Днём Победы!"),
}

private val NEW_YEAR_FROM: MonthDay = MonthDay.of(Month.DECEMBER, 25)
private val NEW_YEAR_TO: MonthDay = MonthDay.of(Month.JANUARY, 8)

/** Какой праздник оформлять в этот день, или null в обычный день. */
fun holidayOn(date: LocalDate): Holiday? {
    val day = MonthDay.from(date)
    return when {
        !day.isBefore(NEW_YEAR_FROM) || !day.isAfter(NEW_YEAR_TO) -> Holiday.NEW_YEAR
        day == MonthDay.of(Month.FEBRUARY, 23) -> Holiday.DEFENDERS_DAY
        day == MonthDay.of(Month.MARCH, 8) -> Holiday.WOMENS_DAY
        day == MonthDay.of(Month.MAY, 1) -> Holiday.SPRING_DAY
        day == MonthDay.of(Month.MAY, 9) -> Holiday.VICTORY_DAY
        else -> null
    }
}
