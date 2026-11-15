package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatTime

/**
 * Строка одной траты в списке с категорией, временем, местом, заметкой и суммой.
 */
@Composable
fun ExpenseRow(
    row: ExpenseWithCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labels: List<ExpenseLabelHit> = emptyList(),
) {
    val e = row.expense
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(
            emoji = row.categoryEmoji,
            categoryName = row.categoryName,
            size = 42.dp,
        )
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            val title = remember(row) { listOfNotNull(row.subcategoryName ?: row.categoryName, e.place).joinToString(" · ") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = TratyTheme.typography.titleSmall,
                    color = TratyTheme.colors.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (e.source == ExpenseSource.DEBT) {
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Surface(
                        shape = TratyTheme.shapes.small,
                        color = TratyTheme.colors.primaryContainer,
                    ) {
                        Text(
                            text = "Частями",
                            style = TratyTheme.typography.labelSmall,
                            color = TratyTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (labels.isNotEmpty()) {
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    ExpenseLabelBadges(labels)
                }
            }
            val sub = remember(row) { listOfNotNull(formatTime(e.timestamp), e.note).joinToString(" · ") }
            Text(
                text = sub,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        TratyAmountText(
            amountKopecks = e.amountKopecks,
            size = AmountSize.MEDIUM,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
