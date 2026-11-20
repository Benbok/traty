package ru.my.traty.ui.designsystem.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.indication
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun Modifier.tratyPressFeedback(
    interactionSource: InteractionSource,
    shape: Shape? = null,
): Modifier = indication(
    interactionSource,
    TratyPressIndication(TratyTheme.colors.onSurface, shape, drawFocusAndHover = false),
)

internal data class TratyPressIndication(
    val color: Color,
    val shape: Shape? = null,
    val drawFocusAndHover: Boolean = true,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TratyPressNode(interactionSource, this)
}

private class TratyPressNode(
    private val interactionSource: InteractionSource,
    private val indication: TratyPressIndication,
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
        val alpha = if (indication.drawFocusAndHover) {
            maxOf(pressAlpha, if (focused) 0.10f else 0f, if (hovered) 0.04f else 0f)
        } else {
            pressAlpha
        }
        if (alpha <= 0f) return
        val color = indication.color.copy(alpha = alpha)
        val shape = indication.shape
        if (shape == null) {
            drawRect(color)
        } else {
            drawOutline(shape.createOutline(size, layoutDirection, this), color)
        }
    }
}
