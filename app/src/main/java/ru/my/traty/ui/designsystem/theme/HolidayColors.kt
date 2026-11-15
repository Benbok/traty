package ru.my.traty.ui.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.my.traty.ui.designsystem.tokens.TratyColors

/** Праздник, который сейчас оформляется; null — обычный день или оформление выключено. */
val LocalTratyHoliday = staticCompositionLocalOf<Holiday?> { null }

/** Акцент праздника для светлой и тёмной темы. Контраст текста не ниже 4.5:1, как у основной палитры. */
private class HolidayAccent(
    val light: Accent,
    val dark: Accent,
)

private class Accent(val primary: Color, val container: Color, val onContainer: Color)

private fun accentOf(holiday: Holiday): HolidayAccent = when (holiday) {
    Holiday.NEW_YEAR -> HolidayAccent( // зимний синий
        light = Accent(Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
        dark = Accent(Color(0xFF60A5FA), Color(0xFF1E3A8A), Color(0xFFDBEAFE)),
    )
    Holiday.DEFENDERS_DAY -> HolidayAccent( // оливковый
        light = Accent(Color(0xFF4D7C0F), Color(0xFFECFCCB), Color(0xFF365314)),
        dark = Accent(Color(0xFFA3E635), Color(0xFF365314), Color(0xFFECFCCB)),
    )
    Holiday.WOMENS_DAY -> HolidayAccent( // тюльпан
        light = Accent(Color(0xFFBE185D), Color(0xFFFCE7F3), Color(0xFF831843)),
        dark = Accent(Color(0xFFF472B6), Color(0xFF831843), Color(0xFFFCE7F3)),
    )
    Holiday.SPRING_DAY -> HolidayAccent( // сирень
        light = Accent(Color(0xFF6D28D9), Color(0xFFEDE9FE), Color(0xFF4C1D95)),
        dark = Accent(Color(0xFFA78BFA), Color(0xFF4C1D95), Color(0xFFEDE9FE)),
    )
    Holiday.VICTORY_DAY -> HolidayAccent( // золото
        light = Accent(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFF78350F)),
        dark = Accent(Color(0xFFFBBF24), Color(0xFF78350F), Color(0xFFFEF3C7)),
    )
}

/** Меняет только акцент; фоны и цвета трат и доходов остаются прежними. */
internal fun TratyColors.withHoliday(holiday: Holiday): TratyColors {
    val a = accentOf(holiday).let { if (isDark) it.dark else it.light }
    return copy(
        primary = a.primary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
    )
}
