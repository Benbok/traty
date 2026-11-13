package ru.my.traty.ui.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Плавный прогресс-бар для отображения доли категории в бюджете.
 */
@Composable
fun TratyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = TratyTheme.colors.primary,
    trackColor: Color = TratyTheme.colors.surfaceContainerHigh,
    height: Dp = 6.dp,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progressAnimation",
    )

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clip(TratyTheme.shapes.pill)
            .background(trackColor),
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(TratyTheme.shapes.pill)
                    .background(color),
            )
        }
    }
}
