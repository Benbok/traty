package ru.my.traty.ui.designsystem.theme

import androidx.compose.material3.ColorScheme
import ru.my.traty.ui.designsystem.tokens.TratyColors

/**
 * Цвета из обоев (Android 12+) поверх палитры Traty.
 *
 * Из системной схемы берутся акцент, фоны, карточки и рамки. Смысловые цвета
 * (траты, доходы, предупреждения, подсказки) остаются свои: красный всегда значит «расход»,
 * зелёный — «доход», какие бы обои ни стояли.
 */
internal fun TratyColors.withSystemColors(scheme: ColorScheme): TratyColors = copy(
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    primaryContainer = scheme.primaryContainer,
    onPrimaryContainer = scheme.onPrimaryContainer,
    background = scheme.background,
    onBackground = scheme.onBackground,
    surface = scheme.surface,
    onSurface = scheme.onSurface,
    surfaceVariant = scheme.surfaceVariant,
    onSurfaceVariant = scheme.onSurfaceVariant,
    surfaceContainer = scheme.surfaceContainer,
    surfaceContainerHigh = scheme.surfaceContainerHigh,
    border = scheme.outlineVariant,
    borderSubtle = scheme.surfaceContainerHigh,
)
