package ru.my.traty.ui.designsystem.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Скругления элементов интерфейса Traty.
 * Современные плавные скругления (squircle-like feel).
 */
@Immutable
data class TratyShapes(
    val extraSmall: Shape = RoundedCornerShape(6.dp),
    val small: Shape = RoundedCornerShape(10.dp),
    val medium: Shape = RoundedCornerShape(16.dp),
    val large: Shape = RoundedCornerShape(24.dp),
    val extraLarge: Shape = RoundedCornerShape(32.dp),
    val pill: Shape = CircleShape,
    val card: Shape = RoundedCornerShape(18.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

val LocalTratyShapes = staticCompositionLocalOf { TratyShapes() }
