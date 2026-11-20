package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Основная кнопка действия (Primary CTA).
 */
@Composable
fun TratyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = TratyTheme.colors.primary,
    contentColor: Color = TratyTheme.colors.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.medium
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).tratyPressFeedback(interactionSource, shape),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = TratyTheme.colors.surfaceContainerHigh,
            disabledContentColor = TratyTheme.colors.onSurfaceVariant,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Второстепенная кнопка (Outlined).
 */
@Composable
fun TratyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.medium
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).tratyPressFeedback(interactionSource, shape),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TratyTheme.colors.primary,
            disabledContentColor = TratyTheme.colors.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, if (enabled) TratyTheme.colors.outline else TratyTheme.colors.borderSubtle),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Тональная кнопка для нейтральных и вспомогательных действий.
 */
@Composable
fun TratyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.medium
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).tratyPressFeedback(interactionSource, shape),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = TratyTheme.colors.surfaceContainer,
            contentColor = TratyTheme.colors.onSurface,
        ),
        content = content,
    )
}

/**
 * Текстовая кнопка (Ghost button).
 */
@Composable
fun TratyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = TratyTheme.colors.primary,
    content: @Composable RowScope.() -> Unit,
) {
    TratyMaterialTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = TratyTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
        ),
        content = content,
    )
}

/**
 * Иконка-кнопка с опциональным фоном-подложкой.
 */
@Composable
fun TratyIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = TratyTheme.colors.onSurface,
    containerColor: Color = Color.Transparent,
) {
    TratyMaterialIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = tint,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
