package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.theme.TratyTheme

@OptIn(ExperimentalMaterial3Api::class)
private val dropdownRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.10f, hoveredAlpha = 0.08f, pressedAlpha = 0f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TratyDropdownPicker(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    label: @Composable () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides dropdownRipple) {
        Box {
            OutlinedButton(
                onClick = onExpand,
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            ) { label() }
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = options)
        }
    }
}
