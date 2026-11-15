package ru.my.traty.ui.recurring

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ru.my.traty.data.Recurring
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton

/** Диалоги экрана «Подписки»: «Сколько списалось?» и удаление. Форма платежа теперь на экране EditRecurringScreen. */
@Composable
internal fun PaidAmountDialog(item: Recurring, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf(AmountInput.fromKopecks(item.amountKopecks)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Сколько списалось?", style = TratyTheme.typography.titleMedium) },
        text = {
            TratyAmountTextField(
                value = amount,
                onValueChange = { amount = it },
                label = item.title,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    AmountInput.parsePositiveKopecks(amount)?.let(onConfirm)
                },
            ) { Text("Списалось") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Удаление платежа нельзя отменить, поэтому спрашиваем ещё раз. Прошлые траты по нему остаются. */
@Composable
internal fun DeleteRecurringDialog(item: Recurring, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Удалить «${item.title}»?", style = TratyTheme.typography.titleMedium) },
        text = {
            Text(
                "Платёж пропадёт из списка и напоминаний. Уже записанные траты останутся.",
                style = TratyTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TratyTextButton(onClick = onConfirm, contentColor = TratyTheme.colors.expense) { Text("Удалить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

internal val UNIT_CHIPS = listOf(
    PeriodUnit.DAY to "Дни",
    PeriodUnit.WEEK to "Недели",
    PeriodUnit.MONTH to "Месяцы",
    PeriodUnit.YEAR to "Годы",
)
internal val REMIND_CHIPS = listOf(0 to "Нет", 1 to "За 1 день", 3 to "За 3 дня", 7 to "За 7 дней")
