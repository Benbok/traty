package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.Recurring
import ru.my.traty.data.recurring.nextDate
import ru.my.traty.data.recurring.period
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub

/**
 * Спрашивает, добавить ли трату в «Подписки». Показывается, только когда [visible] и есть предложение.
 */
@Composable
fun RecurringOfferHost(visible: Boolean = true, vm: RecurringOfferViewModel = viewModel()) {
    val offer by vm.offer.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val current = offer ?: return
    if (!visible) return
    RecurringOfferDialog(
        offer = current,
        error = error,
        onAccept = { vm.accept(current) },
        onDecline = { vm.decline(current) },
        onDismiss = { vm.skip(current) },
    )
}

@Composable
fun RecurringOfferDialog(
    offer: Recurring,
    error: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Добавить в «Подписки»?", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                Text(
                    "Похоже на регулярный платёж: «${offer.title}», ${formatRub(offer.amountKopecks)} ${offer.period().label()}. " +
                        "Следующее списание ${formatDate(offer.nextDate())}.",
                    style = TratyTheme.typography.bodyMedium,
                )
                Text(
                    "Если это разовая трата, нажмите «Не надо»: трата сохранена, а в «Подписки» ничего не добавится.",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.expense)
                }
            }
        },
        confirmButton = { TratyButton(onClick = onAccept) { Text("Добавить") } },
        dismissButton = { TratyTextButton(onClick = onDecline) { Text("Не надо") } },
    )
}
