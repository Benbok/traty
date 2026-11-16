package ru.my.traty.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.items.ProductTotal
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/** Строка товара: название, сумма, полоска доли от самого дорогого товара, «3 покупки · Пятёрочка». */
@Composable
internal fun ProductRow(p: ProductTotal, maxKopecks: Long, showPlaces: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = TratyTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(formatRub(p.totalKopecks), style = TratyTheme.typography.titleSmall)
        }
        if (maxKopecks > 0) {
            TratyProgressBar(progress = p.totalKopecks.toFloat() / maxKopecks, modifier = Modifier.fillMaxWidth())
        }
        Text(ItemTexts.subtitle(p, showPlaces), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}
