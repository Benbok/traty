package ru.my.traty.ui.budget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import ru.my.traty.data.budget.PlanMonth
import ru.my.traty.data.budget.Promised
import ru.my.traty.data.budget.TotalProgress
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.recurring.canNotify
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatMonth
import ru.my.traty.ui.formatRub

/**
 * Карточка общего лимита на экране плана: сколько всего можно потратить за месяц по всем категориям.
 * Нет лимита — короткое приглашение задать его.
 */
@Composable
fun TotalLimitCard(total: TotalProgress?, promised: Promised, categoriesPlanned: Long, period: BillingPeriod, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TratyCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Общий лимит на месяц", style = TratyTheme.typography.titleSmall)
            Spacer(Modifier.height(TratyTheme.spacing.xxs))
            if (total == null) {
                Text(
                    "Не задан. Нажмите, чтобы ограничить все траты месяца одной суммой.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                "${formatRub(total.spent)} из ${formatRub(total.planned)}",
                style = TratyTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            PlanBar(fraction = total.fraction, color = planColor(total.status))
            Text(
                totalStatusText(total, period),
                style = TratyTheme.typography.bodySmall,
                color = if (total.remaining < 0) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
            )
            PromisedBlock(promised, remaining = total.remaining, period = period)
            if (categoriesPlanned > total.planned) {
                Text(
                    "Планы категорий в сумме ${formatRub(categoriesPlanned)}, это больше общего лимита.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.warning,
                )
            }
            Text(
                totalRepeatText(total, PlanMonth.of(period)),
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private fun totalStatusText(t: TotalProgress, period: BillingPeriod): String {
    val today = java.time.LocalDate.now()
    return when {
        today.isBefore(period.startDate) -> "Лимит на будущий период"
        t.remaining < 0 -> "Перерасход ${formatRub(-t.remaining)}"
        today.isAfter(period.endDate) -> "Уложились, осталось ${formatRub(t.remaining)}"
        t.perDay != null -> "Осталось ${formatRub(t.remaining)} · ${formatRub(t.perDay)} в день"
        else -> "Осталось ${formatRub(t.remaining)}"
    }
}

private fun totalRepeatText(t: TotalProgress, month: Int): String = when {
    t.limit.month != month -> "Повторяется с ${formatMonth(PlanMonth.toYearMonth(t.limit.month)).lowercase()}"
    t.limit.repeats -> "Повторяется каждый месяц"
    else -> "Только на этот месяц"
}

/** Диалог общего лимита: сумма, повтор и «убрать с этого месяца». */
@Composable
fun TotalLimitDialog(
    current: TotalProgress?,
    repeatsNow: Boolean,
    monthTitle: String,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var amount by remember { mutableStateOf(current?.let { AmountInput.fromKopecks(it.planned) } ?: "") }
    var repeats by remember { mutableStateOf(repeatsNow) }
    val kopecks = AmountInput.parsePositiveKopecks(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Общий лимит · $monthTitle", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                Text(
                    "Сколько всего хотите потратить за месяц, по всем категориям вместе.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                TratyAmountTextField(value = amount, onValueChange = { amount = it }, label = "Сумма на месяц", modifier = Modifier.fillMaxWidth())
                FilterChip(selected = repeats, onClick = { repeats = !repeats }, label = { Text("Повторять каждый месяц") })
                if (current != null) {
                    TratyTextButton(onClick = onRemove) { Text("Убрать лимит с этого месяца", color = TratyTheme.colors.expense) }
                }
            }
        },
        confirmButton = {
            TratyButton(enabled = kopecks != null, onClick = { kopecks?.let { onSave(it, repeats) } }) { Text("Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Переключатель уведомлений о плане. На Android 13+ при включении спрашивает разрешение. */
@Composable
fun BudgetAlertsCard(enabled: Boolean, hasPlans: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled, hasPlans) {
        if (enabled && hasPlans && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.canNotify()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    TratyCard(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Уведомлять о плане", style = TratyTheme.typography.titleSmall)
                Text(
                    "Когда потрачено 80 % плана категории или общего лимита и когда план выбран.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

/** Вход в «Задания на неделю» с экрана плана. */
@Composable
fun WeeklyTasksEntryCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TratyCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text("Задания на неделю", style = TratyTheme.typography.titleSmall)
            Text(
                "1–3 небольших задания из ваших трат, итог в воскресенье и серия выполненных недель.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}
