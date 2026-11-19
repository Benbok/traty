package ru.my.traty.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun TratyIllustrationBadge(
    @DrawableRes illustration: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = TratyTheme.shapes.medium,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(illustration),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(shape),
    )
}
