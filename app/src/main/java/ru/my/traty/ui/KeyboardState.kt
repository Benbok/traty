package ru.my.traty.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * Показана ли экранная клавиатура. Состояние меняется только в момент появления и скрытия клавиатуры,
 * а не на каждом кадре её анимации, поэтому экран не перерисовывается лишний раз.
 */
@Composable
fun rememberKeyboardOpen(): State<Boolean> {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    return remember(density, ime) { derivedStateOf { ime.getBottom(density) > 0 } }
}
