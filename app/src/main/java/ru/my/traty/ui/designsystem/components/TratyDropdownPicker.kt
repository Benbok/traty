package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun TratyDropdownPicker(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    label: @Composable () -> Unit,
    matchAnchorWidth: Boolean = false,
    options: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.small
    var anchorWidth by remember { mutableIntStateOf(0) }
    Box {
        OutlinedButton(
            onClick = onExpand,
            modifier = Modifier.fillMaxWidth()
                .then(if (matchAnchorWidth) Modifier.onSizeChanged { anchorWidth = it.width } else Modifier)
                .tratyPressFeedback(interactionSource, shape),
            shape = shape,
            interactionSource = interactionSource,
        ) { label() }
        val menuModifier = if (matchAnchorWidth && anchorWidth > 0) {
            Modifier.width(with(LocalDensity.current) { anchorWidth.toDp() })
        } else Modifier
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = menuModifier, content = options)
    }
}
