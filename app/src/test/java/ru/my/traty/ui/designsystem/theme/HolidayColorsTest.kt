package ru.my.traty.ui.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.my.traty.ui.designsystem.tokens.DarkTratyColors
import ru.my.traty.ui.designsystem.tokens.LightTratyColors

class HolidayColorsTest {
    @Test
    fun everyHolidayChangesAccentOnly() {
        for (base in listOf(LightTratyColors, DarkTratyColors)) {
            for (h in Holiday.entries) {
                val c = base.withHoliday(h)
                assertNotEquals("$h primary", base.primary, c.primary)
                assertNotEquals("$h container", base.primaryContainer, c.primaryContainer)
                assertEquals(base.background, c.background)
                assertEquals(base.surface, c.surface)
                assertEquals(base.expense, c.expense)
                assertEquals(base.income, c.income)
                assertEquals(base.onPrimary, c.onPrimary)
            }
        }
    }

    @Test
    fun lightAndDarkAccentsDiffer() {
        val light = LightTratyColors.withHoliday(Holiday.NEW_YEAR)
        val dark = DarkTratyColors.withHoliday(Holiday.NEW_YEAR)
        assertNotEquals(light.primary, dark.primary)
    }
}
