package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Базовая карточка дизайн-системы Traty.
 * Поддерживает тонкую аккуратную границу (border), закругленные углы и тактильный фон.
 */
@Composable
fun TratyCard(
    modifier: Modifier = Modifier,
    shape: Shape = TratyTheme.shapes.card,
    backgroundColor: Color = TratyTheme.colors.surface,
    border: BorderStroke? = BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clip(shape).clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        shape = shape,
        color = backgroundColor,
        border = border,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Box(content = content)
    }
}

/**
 * Тональная карточка для акцентных блоков и информационных секций.
 */
@Composable
fun TratyTonalCard(
    modifier: Modifier = Modifier,
    shape: Shape = TratyTheme.shapes.card,
    backgroundColor: Color = TratyTheme.colors.surfaceContainer,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    TratyCard(
        modifier = modifier,
        shape = shape,
        backgroundColor = backgroundColor,
        border = border,
        onClick = onClick,
        content = content,
    )
}
