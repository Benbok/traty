package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneId

fun List<FeedItem>.dayHeaders(zone: ZoneId): Map<String, DayHeader> = dayHeaders(
    map { item ->
        val spent = (item as? FeedItem.ExpenseItem)?.data?.expense?.amountKopecks ?: 0L
        DayEntry(item.id, item.timestamp, spent)
    },
    zone,
)

@Composable
fun FeedDayHeader(header: DayHeader, today: LocalDate, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TratyTheme.spacing.lg,
                end = TratyTheme.spacing.lg,
                top = TratyTheme.spacing.md,
                bottom = TratyTheme.spacing.xs,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayTitle(header.date, today),
            style = TratyTheme.typography.labelLarge,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        if (header.expenseTotalKopecks > 0) {
            Text(
                formatRub(header.expenseTotalKopecks),
                style = TratyTheme.typography.labelMedium,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
