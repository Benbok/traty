package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Единый современный заголовочный компонент (Header Card) для экранов приложения Traty.
 * Обеспечивает согласованный внешний вид шапки на всех вкладках (Траты, Графики, Статистика, Настройки)
 * и внутренних экранах (Категории, Шаблоны, Правила, Сверка).
 */
@Composable
fun TratyHeaderCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TratyTheme.spacing.lg,
                end = TratyTheme.spacing.lg,
                top = TratyTheme.spacing.sm,
                bottom = TratyTheme.spacing.xs,
            ),
        shape = TratyTheme.shapes.large,
        backgroundColor = TratyTheme.colors.surface,
        border = BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Иконка возврата на внутренних экранах или тематическая иконка вкладки
                if (onBack != null) {
                    TratyIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        onClick = onBack,
                        containerColor = TratyTheme.colors.surfaceContainer,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                } else if (icon != null) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = TratyTheme.shapes.medium,
                        color = TratyTheme.colors.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = TratyTheme.colors.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                }

                // Заголовок и подзаголовок экрана
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = TratyTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TratyTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = TratyTheme.typography.bodySmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Кнопки действий справа
                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions()
                    }
                }
            }

            // Дополнительный встроенный контент (например, строка поиска)
            if (content != null) {
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                content()
            }
        }
    }
}
