package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import java.time.Instant
import java.time.ZoneId

/**
 * Диалог подтверждения удаления траты, открывающийся при свайпе влево.
 * Содержит раздельные карточки для информации о трате (категория/название/дата)
 * и карточки суммы (цены) для четкого визуального разделения.
 */
@Composable
fun DeleteExpenseConfirmDialog(
    item: ExpenseWithCategory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val e = item.expense
    val zone = remember { ZoneId.systemDefault() }
    val expenseDate = remember(e.timestamp) {
        Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                text = "Удалить запись?",
                style = TratyTheme.typography.headlineMedium,
                color = TratyTheme.colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                // Карточка 1: Название, категория, место и дата
                TratyTonalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(TratyTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TratyCategoryBadge(
                            emoji = item.categoryEmoji,
                            categoryName = item.categoryName,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(TratyTheme.spacing.md))
                        Column(Modifier.weight(1f)) {
                            val title = listOfNotNull(
                                item.subcategoryName ?: item.categoryName,
                                e.place,
                            ).joinToString(" · ")
                            Text(
                                text = title,
                                style = TratyTheme.typography.titleMedium,
                                color = TratyTheme.colors.onSurface,
                            )
                            val sub = listOfNotNull(
                                formatDate(expenseDate),
                                e.note,
                            ).joinToString(" · ")
                            Text(
                                text = sub,
                                style = TratyTheme.typography.bodySmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Карточка 2: Сумма (цена) с акцентным оформлением
                TratyTonalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = TratyTheme.spacing.md,
                                vertical = TratyTheme.spacing.md,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Сумма",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                            Text(
                                text = "Расход",
                                style = TratyTheme.typography.bodySmall,
                                color = TratyTheme.colors.expense,
                            )
                        }
                        TratyAmountText(
                            amountKopecks = e.amountKopecks,
                            size = AmountSize.LARGE,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                Text(
                    text = "Вы уверены, что хотите удалить эту запись?",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TratyButton(
                onClick = onConfirm,
                containerColor = TratyTheme.colors.expense,
                contentColor = Color.White,
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Preview(name = "Delete Expense Dialog - Light", showBackground = true)
@Composable
fun DeleteExpenseConfirmDialogPreviewLight() {
    TratyTheme(darkTheme = false) {
        DeleteExpenseConfirmDialog(
            item = ExpenseWithCategory(
                expense = Expense(
                    id = 1L,
                    amountKopecks = 45000L,
                    categoryId = 1L,
                    place = "ВкусВилл",
                    note = "Сыр и круассан",
                    timestamp = System.currentTimeMillis(),
                    source = ExpenseSource.TEXT,
                ),
                categoryName = "Продукты",
                categoryEmoji = "🛒",
                subcategoryName = "Супермаркет",
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Delete Expense Dialog - Dark", showBackground = true)
@Composable
fun DeleteExpenseConfirmDialogPreviewDark() {
    TratyTheme(darkTheme = true) {
        DeleteExpenseConfirmDialog(
            item = ExpenseWithCategory(
                expense = Expense(
                    id = 1L,
                    amountKopecks = 125000L,
                    categoryId = 2L,
                    place = "Яндекс Такси",
                    note = "До аэропорта",
                    timestamp = System.currentTimeMillis(),
                    source = ExpenseSource.VOICE,
                ),
                categoryName = "Транспорт",
                categoryEmoji = "🚕",
                subcategoryName = null,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
