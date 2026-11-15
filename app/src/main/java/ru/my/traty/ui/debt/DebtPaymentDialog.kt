package ru.my.traty.ui.debt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.AmountVisualTransformation
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.rememberAmountFieldValue
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** «Внести платёж»: сумма с подставленным остатком строки или ближайшего платежа. */
@Composable
internal fun AddPaymentDialog(
    initialAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var rawAmount by remember { mutableStateOf(initialAmount) }
    val (amountValue, onAmountChange) = rememberAmountFieldValue(rawAmount) { rawAmount = it }
    val kopecks = AmountInput.parsePositiveKopecks(rawAmount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внести платёж", style = TratyTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Будет создана трата в категории этой покупки с текущей датой.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = onAmountChange,
                    label = { Text("Сумма платежа") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = AmountVisualTransformation(TratyTheme.colors.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TratyButton(
                onClick = { kopecks?.let { onConfirm(it) } },
                enabled = kopecks != null,
            ) { Text("Внести") }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
