package ru.my.traty.ui.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.ui.designsystem.tokens.DarkTratyColors
import ru.my.traty.ui.designsystem.tokens.LightTratyColors

class DynamicTratyColorsTest {
    private val wallpaperBlue = Color(0xFF3355AA)
    private val wallpaperSurface = Color(0xFFEEF0FF)

    @Test
    fun takesAccentAndSurfacesFromSystem() {
        val scheme = lightColorScheme(primary = wallpaperBlue, surface = wallpaperSurface)
        val colors = LightTratyColors.withSystemColors(scheme)
        assertEquals(wallpaperBlue, colors.primary)
        assertEquals(wallpaperSurface, colors.surface)
        assertEquals(scheme.outlineVariant, colors.border)
        assertEquals(scheme.outline, colors.outline)
    }

    @Test
    fun keepsMoneyColorsAndDarkFlag() {
        val colors = DarkTratyColors.withSystemColors(darkColorScheme(primary = wallpaperBlue))
        assertEquals(DarkTratyColors.expense, colors.expense)
        assertEquals(DarkTratyColors.expenseContainer, colors.expenseContainer)
        assertEquals(DarkTratyColors.income, colors.income)
        assertEquals(DarkTratyColors.incomeContainer, colors.incomeContainer)
        assertEquals(DarkTratyColors.warning, colors.warning)
        assertEquals(DarkTratyColors.info, colors.info)
        assertEquals(true, colors.isDark)
    }
}
