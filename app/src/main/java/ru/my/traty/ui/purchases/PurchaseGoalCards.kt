package ru.my.traty.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.purchases.PurchaseGoalStatus
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub
import java.time.LocalDate

internal val PurchaseGoalStatus.dueDate: LocalDate
    get() = LocalDate.ofEpochDay(goal.dueEpochDay)

internal fun List<PurchaseGoalStatus>.activeGoals(today: LocalDate): List<PurchaseGoalStatus> =
    filter { it.isActive }
        .sortedWith(compareBy({ it.remainingToday(today) == 0L }, { it.goal.dueEpochDay }, { it.goal.id }))

@Composable
internal fun PurchaseGoalsHomeBlock(
    goals: List<PurchaseGoalStatus>,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAction: (PurchaseForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        if (goals.isEmpty()) {
            TratyTextButton(onClick = onOpen) { Text("Накопления на покупки") }
        } else {
            val remaining = goals.fold(0L) { total, goal ->
                total + goal.remainingToday(today).coerceAtMost(Long.MAX_VALUE - total)
            }
            PurchaseGoalsSummary(remaining, expanded, onToggle)
            if (expanded) {
                goals.forEach { status -> PurchaseGoalCard(status, today, onAction) }
                TratyTextButton(onClick = onOpen) { Text("Все цели") }
            }
        }
    }
}

@Composable
private fun PurchaseGoalsSummary(remaining: Long, expanded: Boolean, onToggle: () -> Unit) {
    val colors = TratyTheme.colors
    TratyTonalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = colors.surfaceContainer,
        onClick = onToggle,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Savings, contentDescription = null, tint = colors.primary)
            Text(
                "Отложить на покупки",
                modifier = Modifier.weight(1f),
                style = TratyTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                if (remaining > 0L) formatRub(remaining) else "На сегодня всё",
                style = TratyTheme.typography.labelLarge,
                color = colors.primary,
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть цели" else "Развернуть цели",
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PurchaseGoalCard(
    status: PurchaseGoalStatus,
    today: LocalDate,
    onAction: (PurchaseForm) -> Unit,
) {
    val goal = status.goal
    val due = status.dueDate
    val remaining = status.remainingToday(today)
    val enough = status.savedKopecks >= goal.targetKopecks
    TratyCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text(goal.title, style = TratyTheme.typography.titleMedium)
            Text("К ${formatDate(due)}", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            Text(
                "Отложено ${formatRub(status.savedKopecks).removeSuffix(" ₽")} из ${formatRub(goal.targetKopecks)}",
                style = TratyTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { (status.savedKopecks.toFloat() / goal.targetKopecks).coerceIn(0f, 1f) },
                color = TratyTheme.colors.primary,
                trackColor = TratyTheme.colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    enough -> "Можно покупать"
                    due.isBefore(today) -> "Срок прошёл. Осталось ${formatRub(remaining)}"
                    remaining > 0 -> "Сегодня осталось отложить ${formatRub(remaining)}"
                    else -> "На сегодня отложено"
                },
                color = TratyTheme.colors.onSurfaceVariant,
                style = TratyTheme.typography.bodyMedium,
            )
            TratyButton(
                onClick = {
                    onAction(if (enough) PurchaseForm.Finish(goal.id) else PurchaseForm.Contribute(goal.id))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        enough -> "Купил"
                        remaining > 0L -> "Отложить ${formatRub(remaining)}"
                        else -> "Отложить ещё"
                    },
                )
            }
            if (!enough) {
                TratyTextButton(onClick = { onAction(PurchaseForm.ChangeDate(goal.id)) }) {
                    Text(if (due.isBefore(today)) "Перенести дату" else "Изменить дату")
                }
            }
            TratyTextButton(onClick = { onAction(PurchaseForm.Cancel(goal.id)) }) {
                Text(if (status.savedKopecks > 0L) "Отменить накопление" else "Удалить цель")
            }
        }
    }
}

@Composable
internal fun CompletedPurchaseCard(status: PurchaseGoalStatus, onHide: () -> Unit) {
    TratyCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Text(status.goal.title, style = TratyTheme.typography.titleMedium)
            status.goal.actualPriceKopecks?.let { price ->
                Text(
                    "Куплено за ${formatRub(price)}",
                    color = TratyTheme.colors.onSurfaceVariant,
                    style = TratyTheme.typography.bodyMedium,
                )
            }
            TratyTextButton(onClick = onHide) { Text("Скрыть покупку") }
        }
    }
}
