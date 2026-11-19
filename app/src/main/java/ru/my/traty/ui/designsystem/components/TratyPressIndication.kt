package ru.my.traty.ui.designsystem.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun Modifier.tratyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = null,
    indication = TratyPressIndication(TratyTheme.colors.onSurface),
    enabled = enabled,
    onClick = onClick,
)

private data class TratyPressIndication(val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TratyPressNode(interactionSource, color)
}

private class TratyPressNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    // Ровная подсветка не меняет форму, когда карточка раскрывается.
    private var pressAlpha by mutableFloatStateOf(0f)
    private var focused by mutableStateOf(false)
    private var hovered by mutableStateOf(false)

    override fun onAttach() {
        pressAlpha = 0f
        focused = false
        hovered = false
        coroutineScope.launch {
            var pressAnimation: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressAnimation?.cancel()
                        pressAlpha = 0.06f
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressAnimation?.cancel()
                        pressAnimation = launch {
                            animate(pressAlpha, 0f, animationSpec = tween(durationMillis = 120)) { value, _ ->
                                pressAlpha = value
                            }
                        }
                    }
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = maxOf(pressAlpha, if (focused) 0.10f else 0f, if (hovered) 0.04f else 0f)
        if (alpha > 0f) drawRect(color.copy(alpha = alpha))
    }
}
