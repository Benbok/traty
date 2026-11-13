package ru.my.traty.ui.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun TratyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tratyColors = if (darkTheme) DarkTratyColors else LightTratyColors
    val tratyTypography = TratyTypography()
    val tratySpacing = TratySpacing()
    val tratyShapes = TratyShapes()

    val context = LocalContext.current
    val m3ColorScheme = when {
        (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = tratyColors.primary,
            onPrimary = tratyColors.onPrimary,
            primaryContainer = tratyColors.primaryContainer,
            onPrimaryContainer = tratyColors.onPrimaryContainer,
            background = tratyColors.background,
            onBackground = tratyColors.onBackground,
            surface = tratyColors.surface,
            onSurface = tratyColors.onSurface,
            surfaceVariant = tratyColors.surfaceVariant,
            onSurfaceVariant = tratyColors.onSurfaceVariant,
            outline = tratyColors.border,
            error = tratyColors.expense,
        )
        else -> lightColorScheme(
            primary = tratyColors.primary,
            onPrimary = tratyColors.onPrimary,
            primaryContainer = tratyColors.primaryContainer,
            onPrimaryContainer = tratyColors.onPrimaryContainer,
            background = tratyColors.background,
            onBackground = tratyColors.onBackground,
            surface = tratyColors.surface,
            onSurface = tratyColors.onSurface,
            surfaceVariant = tratyColors.surfaceVariant,
            onSurfaceVariant = tratyColors.onSurfaceVariant,
            outline = tratyColors.border,
            error = tratyColors.expense,
        )
    }

    CompositionLocalProvider(
        LocalTratyColors provides tratyColors,
        LocalTratyTypography provides tratyTypography,
        LocalTratySpacing provides tratySpacing,
        LocalTratyShapes provides tratyShapes,
    ) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            content = content,
        )
    }
}
