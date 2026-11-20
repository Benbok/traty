package ru.my.traty.ui.designsystem.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import ru.my.traty.ui.designsystem.components.TratyPressIndication
import ru.my.traty.ui.designsystem.tokens.DarkTratyColors
import ru.my.traty.ui.designsystem.tokens.LightTratyColors
import ru.my.traty.ui.designsystem.tokens.LocalTratyColors
import ru.my.traty.ui.designsystem.tokens.LocalTratyShapes
import ru.my.traty.ui.designsystem.tokens.LocalTratySpacing
import ru.my.traty.ui.designsystem.tokens.LocalTratyTypography
import ru.my.traty.ui.designsystem.tokens.TratyColors
import ru.my.traty.ui.designsystem.tokens.TratyShapes
import ru.my.traty.ui.designsystem.tokens.TratySpacing
import ru.my.traty.ui.designsystem.tokens.TratyTypography

/**
 * Единая точка доступа к токенам дизайн-системы Traty.
 * Пример:
 * Text(color = TratyTheme.colors.primary, style = TratyTheme.typography.heroAmount)
 * Modifier.padding(TratyTheme.spacing.lg)
 */
object TratyTheme {
    val colors: TratyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTratyColors.current

    val typography: TratyTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTratyTypography.current

    val spacing: TratySpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalTratySpacing.current

    val shapes: TratyShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalTratyShapes.current
}

// Компоненты Material рисуют ripple отдельно от LocalIndication. Оставляем видимый
// фокус и наведение, а подсветку касания рисуют компоненты дизайн-системы.
@OptIn(ExperimentalMaterial3Api::class)
private val tratyMaterialRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.10f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0f,
    ),
)

/**
 * Тема приложения. [dynamicColor] включает цвета из обоев на Android 12+;
 * на более старых телефонах остаётся палитра Traty.
 * [holiday] — праздничный акцент и значок в шапке. При цветах из обоев акцент
 * не меняется (иначе он спорит с фоном из обоев), остаётся только значок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TratyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    holiday: Holiday? = null,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) DarkTratyColors else LightTratyColors
    val tratyTypography = TratyTypography()
    val tratySpacing = TratySpacing()
    val tratyShapes = TratyShapes()

    val context = LocalContext.current
    val systemScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    // Экраны берут цвета из TratyTheme.colors, поэтому цвета обоев переносятся и туда
    val tratyColors = when {
        systemScheme != null -> baseColors.withSystemColors(systemScheme)
        holiday != null -> baseColors.withHoliday(holiday)
        else -> baseColors
    }
    val m3ColorScheme = systemScheme?.copy(error = tratyColors.expense) ?: tratyColorScheme(tratyColors, darkTheme)

    CompositionLocalProvider(
        LocalTratyColors provides tratyColors,
        LocalTratyTypography provides tratyTypography,
        LocalTratySpacing provides tratySpacing,
        LocalTratyShapes provides tratyShapes,
        LocalTratyHoliday provides holiday,
    ) {
        MaterialTheme(colorScheme = m3ColorScheme) {
            CompositionLocalProvider(
                LocalIndication provides TratyPressIndication(tratyColors.onSurface),
                LocalRippleConfiguration provides tratyMaterialRipple,
                content = content,
            )
        }
    }
}

private fun tratyColorScheme(c: TratyColors, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        // Без этого чипы, индикатор нижнего меню и переключатели берут сиреневые цвета Material по умолчанию
        secondary = c.primary,
        onSecondary = c.onPrimary,
        secondaryContainer = c.primaryContainer,
        onSecondaryContainer = c.onPrimaryContainer,
        tertiary = c.info,
        surfaceTint = c.primary,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariant,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHigh,
        outline = c.outline,
        outlineVariant = c.border,
        error = c.expense,
        errorContainer = c.expenseContainer,
    )
}
