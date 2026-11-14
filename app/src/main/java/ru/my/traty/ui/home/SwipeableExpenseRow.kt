package ru.my.traty.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.isUserEditable
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Строка траты с поддержкой свайпа влево для быстрого удаления.
 * При сдвиге строки влево открывается акцентная коралловая подложка с иконкой корзины.
 */
@Composable
fun SwipeableExpenseRow(
    item: ExpenseWithCategory,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    labels: List<ExpenseLabelHit> = emptyList(),
) {
    val canDelete = item.expense.isUserEditable
    val density = LocalDensity.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold

    val dismissState = remember(item.expense.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                    currentOnDelete()
                    false
                } else {
                    false
                }
            },
            positionalThreshold = threshold,
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canDelete,
        gesturesEnabled = canDelete,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            // Красную подложку строим только во время свайпа. Иначе каждая строка списка при первом
            // показе собирала бы лишнее дерево (фон, иконка, текст, анимация) — а их никто не видит.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                DismissBackground(dismissState)
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TratyTheme.colors.background),
        ) {
            ExpenseRow(
                row = item,
                onClick = onClick,
                labels = labels,
            )
        }
    }
}

@Composable
private fun DismissBackground(
    dismissState: SwipeToDismissBoxState,
) {
    val isDismissing = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    val scale by animateFloatAsState(
        targetValue = if (isDismissing) 1.15f else 0.85f,
        label = "trash_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TratyTheme.colors.expense)
            .padding(horizontal = TratyTheme.spacing.xl),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Удалить",
                tint = Color.White,
                modifier = Modifier.scale(scale),
            )
            Text(
                text = "Удалить",
                color = Color.White,
                style = TratyTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
