package ru.my.traty.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.data.debt.DebtOverview
import ru.my.traty.data.debt.DebtOverviewLine
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalDate

/** Размеры элементов строки (не отступы: отступы — из [TratyTheme.spacing]). */
private val BadgeSize = 36.dp
private val ProgressHeight = 6.dp

/** Покупка в карточке долгов: название, остаток, ближайший платёж. Нажатие открывает дерево платежей. */
@Composable
internal fun DebtOverviewRow(line: DebtOverviewLine, category: DebtCategoryLabel?, today: LocalDate, onClick: () -> Unit) {
    val overdue = line.summary.overdueCount > 0 || line.next?.overdue == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(emoji = category?.emoji ?: "📦", categoryName = category?.name.orEmpty(), size = BadgeSize)
        Spacer(Modifier.width(TratyTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.debt.title,
                    style = TratyTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " · ${DebtOverviewTexts.kind(line.debt)}",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            Text(DebtOverviewTexts.left(line), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            if (line.summary.totalKopecks != null) {
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                TratyProgressBar(
                    progress = line.summary.progressPercent,
                    color = if (overdue) TratyTheme.colors.expense else TratyTheme.colors.primary,
                    height = ProgressHeight,
                )
            }
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                DebtOverviewTexts.next(line, today),
                style = TratyTheme.typography.labelSmall,
                color = if (overdue) TratyTheme.colors.expense else TratyTheme.colors.onSurface,
                fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Дубли с подписками и кредиты, заведённые только подпиской. */
@Composable
internal fun DebtsNotices(o: DebtOverview) {
    val notices = o.duplicates.mapNotNull { DebtOverviewTexts.duplicate(it) } +
        listOfNotNull(DebtOverviewTexts.subscriptionOnly(o.subscriptionOnly))
    notices.forEach { text ->
        Spacer(Modifier.height(TratyTheme.spacing.xs))
        Surface(shape = TratyTheme.shapes.small, color = TratyTheme.colors.surfaceContainer) {
            Text(
                "⚠️ $text",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
            )
        }
    }
}
