package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.Income
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatTime

/**
 * Строка пополнения (дохода) в ленте — отображается зелёным со знаком «+».
 */
@Composable
fun IncomeRow(
    income: Income,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(
            emoji = "💰",
            categoryName = income.source,
            size = 42.dp,
        )
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = income.source,
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.income,
            )
            val sub = listOfNotNull(formatTime(income.timestamp), income.note).joinToString(" · ")
            Text(
                text = sub,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        Text(
            text = "+${formatRub(income.amountKopecks)}",
            style = TratyTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TratyTheme.colors.income,
        )
    }
}
