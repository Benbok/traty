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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatTime
import ru.my.traty.ui.pluralRu

/**
 * Строка одной траты в списке с категорией, временем, местом, заметкой и суммой.
 */
@Composable
fun ExpenseRow(
    row: ExpenseWithCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labels: List<ExpenseLabelHit> = emptyList(),
    /** Сколько товаров в покупке: если два и больше, вместо заметки пишется «5 товаров». */
    itemCount: Int = 0,
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
            }
            if (labels.isNotEmpty()) {
                ExpenseLabelBadges(
                    labels,
                    Modifier.padding(top = TratyTheme.spacing.xxs, bottom = TratyTheme.spacing.xxs),
                )
            }
            val accent = TratyTheme.colors.primary
            val sub = remember(row, itemCount, accent) { expenseSubtitleText(formatTime(e.timestamp), e.note, itemCount, accent) }
            Text(
                text = sub,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
                // Длинная заметка не должна раздувать строку ленты; полный текст — в карточке траты
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TratyAmountText(
            amountKopecks = e.amountKopecks,
            size = AmountSize.MEDIUM,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Вторая строка траты в ленте: время и заметка. Если в покупке два товара и больше, заметка (там весь список
 * с ценами) заменяется на «5 товаров»: при 10-20 товарах одна трата иначе занимала бы пол-экрана.
 * Товары видны в карточке траты и под строкой, когда трату нашёл поиск.
 */
internal fun expenseSubtitle(time: String, note: String?, itemCount: Int): String {
    val rest = if (itemCount >= 2) itemsLabel(itemCount) else note?.takeIf { it.isNotBlank() }
    return listOfNotNull(time, rest).joinToString(" · ")
}

/** «5 товаров»: число и слово в нужном склонении. */
private fun itemsLabel(count: Int): String = "$count ${pluralRu(count.toLong(), "товар", "товара", "товаров")}"

/**
 * Та же подпись, но с выделенным цветом [accent] «5 товаров»: видно, что у покупки есть список. Время и обычная
 * заметка остаются серыми, чтобы лента не была пестрой.
 */
internal fun expenseSubtitleText(time: String, note: String?, itemCount: Int, accent: Color): AnnotatedString {
    val text = expenseSubtitle(time, note, itemCount)
    if (itemCount < 2) return AnnotatedString(text)
    val label = itemsLabel(itemCount)
    val start = text.lastIndexOf(label)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium), start, start + label.length)
    }
}
