package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun TratyDropdownPicker(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    label: @Composable () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = TratyTheme.shapes.small
    Box {
        OutlinedButton(
            onClick = onExpand,
            modifier = Modifier.fillMaxWidth().tratyPressFeedback(interactionSource, shape),
            shape = shape,
            interactionSource = interactionSource,
        ) { label() }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = options)
    }
}
