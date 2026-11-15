package ru.my.traty.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ui.designsystem.tokens.DarkTratyColors
import ru.my.traty.ui.designsystem.tokens.LightTratyColors
import ru.my.traty.ui.designsystem.tokens.TratyColors

/** Тексты и суммы читаются в обеих темах: контраст не ниже 4.5:1, рамки полей не ниже 3:1 (WCAG AA). */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
    }

    private fun textColors(c: TratyColors) = mapOf(
        "onSurface" to c.onSurface,
        "onSurfaceVariant" to c.onSurfaceVariant,
        "primary" to c.primary,
        "expense" to c.expense,
        "income" to c.income,
        "warning" to c.warning,
        "info" to c.info,
    )

    private fun check(name: String, c: TratyColors) {
        for (bg in listOf("surface" to c.surface, "background" to c.background)) {
            for ((label, fg) in textColors(c)) {
                val r = contrast(fg, bg.second)
                assertTrue("$name: $label на ${bg.first} = $r", r >= 4.5)
            }
            val o = contrast(c.outline, bg.second)
            assertTrue("$name: outline на ${bg.first} = $o", o >= 3.0)
        }
        assertTrue("$name: onPrimary на primary", contrast(c.onPrimary, c.primary) >= 4.5)
    }

    @Test
    fun lightThemeIsReadable() = check("светлая", LightTratyColors)

    @Test
    fun darkThemeIsReadable() = check("тёмная", DarkTratyColors)

    @Test
    fun holidayAccentsStayReadable() {
        for (base in listOf(LightTratyColors, DarkTratyColors)) {
            for (h in Holiday.entries) {
                val c = base.withHoliday(h)
                assertTrue("$h primary", contrast(c.primary, c.surface) >= 4.5)
            }
        }
    }
}
