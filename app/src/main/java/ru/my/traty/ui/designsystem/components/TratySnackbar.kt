package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette

/**
 * Красивое всплывающее уведомление (Snackbar / Toast) дизайн-системы Traty.
 * Оформлено в строгом графитово-грифельном стиле с мягкой цветной плашкой-иконкой,
 * акцентной изумрудной кнопкой действия («Отменить») и легким парением над интерфейсом.
 */
@Composable
fun TratySnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    TratySnackbarContent(
        message = snackbarData.visuals.message,
        actionLabel = snackbarData.visuals.actionLabel,
        withDismissAction = snackbarData.visuals.withDismissAction,
        onAction = { snackbarData.performAction() },
        onDismiss = { snackbarData.dismiss() },
        modifier = modifier,
    )
}

/**
 * Презентационная версия TratySnackbar для прямого использования и превью.
 */
@Composable
fun TratySnackbarContent(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val hasAction = (actionLabel != null) && (onAction != null)

    val isDeleteNotification = hasAction || message.contains("удален", ignoreCase = true)
    val isSuccessNotification = message.contains("сохранен", ignoreCase = true) ||
        message.contains("добавлен", ignoreCase = true) ||
        message.contains("восстановлен", ignoreCase = true)

    val icon: ImageVector = when {
        isDeleteNotification -> Icons.Outlined.Delete
        isSuccessNotification -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Info
    }

    val iconTint: Color = when {
        isDeleteNotification -> TratyPalette.CoralRose
        isSuccessNotification -> TratyPalette.Emerald400
        else -> TratyPalette.SkyInfo
    }

    val containerColor = if (TratyTheme.colors.isDark) {
        TratyPalette.Slate800
    } else {
        TratyPalette.Slate900
    }

    val borderColor = if (TratyTheme.colors.isDark) {
        TratyPalette.Slate700
    } else {
        TratyPalette.Slate800
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        shape = TratyTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = TratyTheme.shapes.pill,
                    color = iconTint.copy(alpha = 0.16f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.width(TratyTheme.spacing.md))

                Text(
                    text = message,
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyPalette.Slate50,
                    maxLines = 2,
                )
            }

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                TratyMaterialTextButton(
                    onClick = onAction,
                    shape = TratyTheme.shapes.small,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TratyPalette.Emerald400,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = TratyTheme.spacing.md,
                        vertical = TratyTheme.spacing.xs,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = TratyTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            } else if (withDismissAction && onDismiss != null) {
                TratyMaterialIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = TratyPalette.Slate400,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
