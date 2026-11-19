package ru.my.traty.ui.purchases

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun PurchaseGoalAddButton(onClick: () -> Unit) {
    TratyIconButton(
        icon = Icons.Filled.Add,
        contentDescription = "Запланировать покупку",
        onClick = onClick,
        tint = TratyTheme.colors.primary,
        containerColor = TratyTheme.colors.primaryContainer,
    )
}
