package ru.my.traty.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified

/** Ниже этого масштаба шрифт не уменьшаем: дальше подпись обрежется многоточием. */
private const val MinLabelScale = 0.7f

/** Небольшой запас, чтобы округление ширины не вызвало обрезку. */
private const val FitMargin = 0.97f

/**
 * Подпись вкладки нижней панели, всегда в одну строку.
 *
 * Размер шрифта подбирается по самой длинной подписи из [allTitles], поэтому у всех вкладок
 * он одинаковый: подписи стоят на одной линии, а иконки над ними на одной высоте.
 * Высота строки (lineHeight) не меняется, так что вкладки не «прыгают» при узком экране
 * или крупном системном шрифте.
 */
@Composable
fun TratyNavLabel(
    text: String,
    allTitles: List<String>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(maxWidth, style, allTitles, measurer) {
            val longest = if (maxWidth == Constraints.Infinity) {
                0
            } else {
                allTitles.maxOf {
                    measurer.measure(it, style, maxLines = 1, softWrap = false).size.width
                }
            }
            if (longest <= maxWidth) {
                style
            } else {
                val k = (maxWidth.toFloat() / longest * FitMargin).coerceAtLeast(MinLabelScale)
                style.copy(
                    fontSize = style.fontSize * k,
                    letterSpacing = if (style.letterSpacing.isSpecified) {
                        style.letterSpacing * k
                    } else {
                        style.letterSpacing
                    },
                )
            }
        }
        Text(
            text = text,
            style = fitted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
