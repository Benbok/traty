package ru.my.traty.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.tratyPressFeedback
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.navigation.TratyNavLabel
import ru.my.traty.ui.navigation.TratyNavTab

@Composable
internal fun TratyBottomBar(tab: TratyNavTab, onSelect: (TratyNavTab) -> Unit) {
    NavigationBar(
        containerColor = TratyTheme.colors.surface,
        tonalElevation = 2.dp,
    ) {
        val navTitles = remember { TratyNavTab.entries.map { it.title } }
        TratyNavTab.entries.forEach { t ->
            val interactionSource = remember { MutableInteractionSource() }
            NavigationBarItem(
                selected = tab == t,
                onClick = { onSelect(t) },
                modifier = Modifier.tratyPressFeedback(interactionSource, TratyTheme.shapes.medium),
                interactionSource = interactionSource,
                icon = { Icon(t.icon, contentDescription = null) },
                label = {
                    TratyNavLabel(
                        text = t.title,
                        allTitles = navTitles,
                        style = TratyTheme.typography.labelMedium,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TratyTheme.colors.primary,
                    selectedTextColor = TratyTheme.colors.primary,
                    indicatorColor = TratyTheme.colors.primaryContainer,
                    unselectedIconColor = TratyTheme.colors.onSurfaceVariant,
                    unselectedTextColor = TratyTheme.colors.onSurfaceVariant,
                ),
            )
        }
    }
}
