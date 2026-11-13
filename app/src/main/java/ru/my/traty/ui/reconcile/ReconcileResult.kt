package ru.my.traty.ui.reconcile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.ReconcileOutcome
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

private const val MINUS = "−"

/** Шаг 2: «Должно остаться / Фактически», итог сверки, динамика «Неучтённых» и кнопки. */
@Composable
fun ReconcileResult(
    state: ReconcileResultState,
    history: List<HistoryItem>,
    onDone: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outcome = state.outcome
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        if (outcome is ReconcileOutcome.Opening) {
            Verdict(
                title = "Стартовый капитал: ${formatRub(outcome.capitalKopecks)}",
                text = "Отсюда приложение будет считать, сколько денег должно остаться.",
                color = TratyTheme.colors.income,
            )
        } else {
            state.expectedKopecks()?.let { expected ->
                TratyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TratyTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                        AmountRow("Должно остаться", formatRub(expected))
                        AmountRow("Фактически", formatRub(state.actualKopecks))
                    }
                }
            }
            when (outcome) {
                is ReconcileOutcome.Unaccounted -> Verdict(
                    title = "Неучтённые: $MINUS${formatRub(outcome.amountKopecks)}",
                    text = "Деньги ушли мимо записей — чаще всего это мелкие спонтанные покупки.",
                    color = TratyTheme.colors.expense,
                )
                is ReconcileOutcome.Surplus -> Verdict(
                    title = "Лишние: +${formatRub(outcome.amountKopecks)}",
                    text = "Записаны как пополнение «Прочее».",
                    color = TratyTheme.colors.income,
                )
                else -> Verdict(title = "Всё сходится 👍", text = null, color = TratyTheme.colors.income)
            }
        }

        if (history.isNotEmpty()) {
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TratyTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    Text("Неучтённые по периодам", style = TratyTheme.typography.titleSmall)
                    history.forEach { h ->
                        AmountRow(h.label, "$MINUS${formatRub(h.amountKopecks)}")
                    }
                }
            }
        }

        Spacer(Modifier.height(TratyTheme.spacing.xs))
        TratyButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        TratyOutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) { Text("Переделать") }
    }
}

@Composable
private fun Verdict(title: String, text: String?, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        Text(title, style = TratyTheme.typography.headlineMedium, color = color)
        if (text != null) {
            Text(text, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurfaceVariant)
        Text(value, style = TratyTheme.typography.titleMedium)
    }
}
