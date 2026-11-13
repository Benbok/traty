package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

enum class AmountSize {
    HERO,
    LARGE,
    MEDIUM,
    SMALL,
}

/**
 * Отображение финансовой суммы в едином стиле приложения.
 */
@Composable
fun TratyAmountText(
    amountKopecks: Long,
    modifier: Modifier = Modifier,
    size: AmountSize = AmountSize.MEDIUM,
    color: Color = TratyTheme.colors.onSurface,
    fontWeight: FontWeight? = null,
) {
    val style: TextStyle = when (size) {
        AmountSize.HERO -> TratyTheme.typography.heroAmount
        AmountSize.LARGE -> TratyTheme.typography.largeAmount
        AmountSize.MEDIUM -> TratyTheme.typography.mediumAmount
        AmountSize.SMALL -> TratyTheme.typography.bodyMedium
    }

    Text(
        text = formatRub(amountKopecks),
        style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Чип дельты к прошлому периоду (+12% или -5%).
 */
@Composable
fun TratyDeltaBadge(
    current: Long,
    previous: Long,
    modifier: Modifier = Modifier,
) {
    if (previous == 0L) return

    val diff = current - previous
    val pct = kotlin.math.round((diff * 100.0) / previous).toInt()
    val isIncrease = pct > 0
    val text = if (pct > 0) "+$pct%" else "$pct%"

    // Для расходов: рост расходов — это привлечение внимания (красный), снижение — позитив (зеленый)
    val bgColor = if (isIncrease) TratyTheme.colors.expenseContainer else TratyTheme.colors.incomeContainer
    val textColor = if (isIncrease) TratyTheme.colors.expense else TratyTheme.colors.income

    Box(
        modifier = modifier
            .clip(TratyTheme.shapes.extraSmall)
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TratyTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
