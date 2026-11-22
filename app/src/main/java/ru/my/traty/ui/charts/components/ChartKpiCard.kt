package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.charts.model.ChartSummary
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatRubSigned
import ru.my.traty.ui.pluralRu

/**
 * Блок сводных метрик: баланс, доходы, траты, норма сбережений.
 */
@Composable
fun ChartKpiCard(
    summary: ChartSummary,
    modifier: Modifier = Modifier,
) {
    val isBalancePositive = summary.netBalanceKopecks >= 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        // Главная карточка: Чистое сальдо (Баланс)
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = if (isBalancePositive) TratyTheme.colors.surface else TratyTheme.colors.surface,
            shape = TratyTheme.shapes.large,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isBalancePositive) TratyTheme.colors.income else TratyTheme.colors.expense,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(TratyTheme.spacing.xs))
                        Text(
                            text = "Баланс за период",
                            style = TratyTheme.typography.titleSmall,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }

                    if (summary.savingsRatePercent != null) {
                        Text(
                            text = "Сбережения: ${summary.savingsRatePercent}%",
                            style = TratyTheme.typography.labelSmall,
                            color = if (summary.savingsRatePercent >= 0) TratyTheme.colors.income else TratyTheme.colors.expense,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                Text(
                    text = formatRubSigned(summary.netBalanceKopecks),
                    style = TratyTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isBalancePositive) TratyTheme.colors.income else TratyTheme.colors.expense,
                )

                Text(
                    text = "Доходы ${formatRub(summary.totalDebitKopecks)} − траты ${formatRub(summary.totalCreditKopecks)}",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }

        // Две карточки рядом: доходы и траты
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            // Дебет
            TratyCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                backgroundColor = TratyTheme.colors.surface,
                shape = TratyTheme.shapes.medium,
            ) {
                Column(Modifier.padding(TratyTheme.spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = TratyTheme.colors.income,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Доходы",
                            style = TratyTheme.typography.labelMedium,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Text(
                        text = "+${formatRub(summary.totalDebitKopecks)}",
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TratyTheme.colors.income,
                    )
                    Text(
                        text = "${summary.incomeCount} ${pluralRu(summary.incomeCount.toLong(), "поступление", "поступления", "поступлений")}",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }

            // Кредит
            TratyCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                backgroundColor = TratyTheme.colors.surface,
                shape = TratyTheme.shapes.medium,
            ) {
                Column(Modifier.padding(TratyTheme.spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = TratyTheme.colors.expense,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Траты",
                            style = TratyTheme.typography.labelMedium,
                            color = TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Text(
                        text = formatRub(summary.totalCreditKopecks),
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TratyTheme.colors.expense,
                    )
                    Text(
                        text = "${summary.expenseCount} ${pluralRu(summary.expenseCount.toLong(), "трата", "траты", "трат")} · в день ~${formatRub(summary.dailyAvgCreditKopecks)}",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
