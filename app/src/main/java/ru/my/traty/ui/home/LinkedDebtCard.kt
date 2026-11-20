package ru.my.traty.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка «Покупка частями» на экране траты: ведёт в дерево и график платежей. Вёрстка Arty, перенесена без изменений. */
@Composable
internal fun LinkedDebtCard(linkedDebt: Debt, onOpenDebt: (Long) -> Unit) {
    val d = linkedDebt
    TratyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenDebt(d.id) },
        backgroundColor = TratyTheme.colors.surface,
        border = BorderStroke(1.dp, TratyTheme.colors.primary.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(TratyTheme.shapes.medium)
                    .background(TratyTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = TratyTheme.colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(TratyTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                val kindText = when (d.debtKind) {
                    DebtKind.INSTALLMENT -> "Рассрочка"
                    DebtKind.LOAN -> "Кредит"
                    DebtKind.PARTIAL -> "Оплата частями"
                }
                Text(
                    text = "Покупка частями · $kindText",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = d.title,
                    style = TratyTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Открыть дерево и график платежей",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
