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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/** Шаг 1: строки «название - сумма» (карты, наличные, вклады), итог и кнопка «Готово». */
@Composable
fun BalanceCalculator(
    lines: List<CalcLine>,
    totalKopecks: Long,
    isFirst: Boolean,
    replacedActualKopecks: Long?,
    canConfirm: Boolean,
    saving: Boolean,
    onName: (Int, String) -> Unit,
    onAmount: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        Text(
            if (isFirst) {
                "Сколько у вас сейчас денег? Эта сумма станет стартовым капиталом — от неё приложение будет считать остаток дальше."
            } else {
                "Сколько у вас сейчас денег на всех картах и счетах вместе? Можно ввести по строкам и сложить."
            },
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        if (replacedActualKopecks != null) {
            Text(
                "Последняя сверка: фактически ${formatRub(replacedActualKopecks)} — введите верную сумму",
                style = TratyTheme.typography.bodyMedium,
                color = TratyTheme.colors.onSurface,
            )
        }
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { onName(line.id, it) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = TratyTheme.shapes.small,
                )
                OutlinedTextField(
                    value = line.amountText,
                    onValueChange = { onAmount(line.id, it) },
                    label = { Text("Сумма, ₽") },
                    isError = ReconcilePolicy.isInvalidAmount(line.amountText),
                    supportingText = if (ReconcilePolicy.isInvalidAmount(line.amountText)) {
                        { Text("Не число") }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = TratyTheme.shapes.small,
                )
                if (lines.size > 1) {
                    TratyIconButton(Icons.Filled.Close, "Убрать строку", onClick = { onRemove(line.id) })
                }
            }
        }
        TratyTextButton(onClick = onAdd) {
            Text("+ добавить строку")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Итого", style = TratyTheme.typography.titleMedium)
            Text(formatRub(totalKopecks), style = TratyTheme.typography.largeAmount, textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(TratyTheme.spacing.xs))
        TratyButton(onClick = onConfirm, enabled = canConfirm && !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Готово")
        }
    }
}
