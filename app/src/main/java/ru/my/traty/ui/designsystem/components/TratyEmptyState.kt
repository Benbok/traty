package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Стильный блок пустого состояния.
 */
@Composable
fun TratyEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.xxl, vertical = TratyTheme.spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(TratyTheme.shapes.large)
                .background(TratyTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TratyTheme.colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(TratyTheme.spacing.lg))

        Text(
            text = title,
            style = TratyTheme.typography.titleMedium,
            color = TratyTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(TratyTheme.spacing.xs))

        Text(
            text = description,
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (action != null) {
            Spacer(Modifier.height(TratyTheme.spacing.lg))
            action()
        }
    }
}
