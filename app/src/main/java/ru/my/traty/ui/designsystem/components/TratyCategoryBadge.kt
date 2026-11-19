package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette
import kotlin.math.abs

/** Значок категории с иллюстрацией или выбранным эмодзи. */
@Composable
fun TratyCategoryBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    categoryName: String = "",
    size: Dp = 44.dp,
    shape: Shape = TratyTheme.shapes.medium,
    backgroundColor: Color? = null,
) {
    val illustration = categoryIllustration(emoji)
    if (illustration != null) {
        TratyIllustrationBadge(illustration = illustration, size = size, modifier = modifier, shape = shape)
        return
    }

    val bg = backgroundColor ?: run {
        if (TratyTheme.colors.isDark) {
            TratyTheme.colors.surfaceContainerHigh
        } else {
            val paletteIndex = abs(categoryName.hashCode()) % TratyPalette.CategoryColors.size
            TratyPalette.CategoryColors[paletteIndex]
        }
    }

    Box(
        modifier = modifier.size(size).clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = (size.value * 0.48f).sp
        Text(
            text = emoji.ifBlank { "📦" },
            fontSize = fontSize,
        )
    }
}
