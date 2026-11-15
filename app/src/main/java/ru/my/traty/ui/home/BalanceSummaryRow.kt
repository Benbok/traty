package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/** Итоги над лентой «Трат»: расходы, а если есть доходы, то ещё доходы и баланс. Суммы в копейках. */
@Composable
fun BalanceSummaryRow(totalExpense: Long, totalIncome: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryValue("Расходы", formatRub(totalExpense), TratyTheme.colors.expense)
        if (totalIncome > 0) {
            SummaryValue("Доходы", "+${formatRub(totalIncome)}", TratyTheme.colors.income)
            val balance = totalIncome - totalExpense
            val sign = if (balance >= 0) "+" else "−"
            val color = if (balance >= 0) TratyTheme.colors.income else TratyTheme.colors.expense
            SummaryValue("Баланс", sign + formatRub(kotlin.math.abs(balance)), color)
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
        Text(value, style = TratyTheme.typography.titleSmall, color = color)
    }
}
