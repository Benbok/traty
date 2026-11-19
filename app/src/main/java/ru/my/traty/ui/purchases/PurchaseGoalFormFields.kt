package ru.my.traty.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.data.purchases.PurchaseGoalStatus
import ru.my.traty.data.purchases.dailyPurchaseAmount
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.DatePickerModal
import java.time.LocalDate

@Composable
internal fun NewPurchaseForm(today: LocalDate, busy: Boolean, onSave: (String, Long, LocalDate) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dueDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val due = dueDay?.let(LocalDate::ofEpochDay)
    val amount = AmountInput.parsePositiveKopecks(amountText)
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Название покупки") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
    )
    TratyAmountTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), "Стоимость", !busy)
    PurchaseDateField(due, today, enabled = !busy) { dueDay = it.toEpochDay() }
    if (amount != null && due != null && !due.isBefore(today)) {
        DailyPurchaseAmountCard(dailyPurchaseAmount(amount, today, due))
    }
    TratyButton(
        onClick = { if (amount != null && due != null) onSave(title.trim(), amount, due) },
        enabled = !busy && title.isNotBlank() && amount != null && due != null && !due.isBefore(today),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Создать цель") }
}

@Composable
private fun DailyPurchaseAmountCard(amountKopecks: Long) {
    TratyTonalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        ) {
            Text(
                "Откладывать в день",
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.onPrimaryContainer,
            )
            TratyAmountText(
                amountKopecks = amountKopecks,
                size = AmountSize.LARGE,
                color = TratyTheme.colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun ChangePurchaseDateForm(
    status: PurchaseGoalStatus,
    today: LocalDate,
    busy: Boolean,
    onSave: (LocalDate) -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(status.goal.dueEpochDay) }
    val date = LocalDate.ofEpochDay(day)
    PurchaseFormHint(status.goal.title)
    PurchaseDateField(date, today, enabled = !busy) { day = it.toEpochDay() }
    TratyButton(
        onClick = { onSave(date) },
        enabled = !busy && !date.isBefore(today) && date != status.dueDate,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Сохранить дату") }
}

@Composable
internal fun ContributePurchaseForm(
    status: PurchaseGoalStatus,
    today: LocalDate,
    busy: Boolean,
    onSave: (Long) -> Unit,
) {
    val suggested = status.remainingToday(today)
    PurchaseFormHint(status.goal.title)
    PurchaseAmountForm(
        initial = suggested.takeIf { it > 0 },
        label = "Взнос",
        button = "Подтвердить взнос",
        busy = busy,
        onSave = onSave,
    ) { amount ->
        val tomorrow = status.recommendationTomorrow(today, amount)
        PurchaseFormHint(
            if (tomorrow == 0L) "Цель будет собрана"
            else "Завтра отложить ${formatRub(tomorrow)}",
        )
    }
}

@Composable
internal fun FinishPurchaseForm(status: PurchaseGoalStatus, busy: Boolean, onSave: (Long) -> Unit) {
    PurchaseFormHint(status.goal.title)
    PurchaseAmountForm(
        initial = status.goal.targetKopecks,
        label = "Фактическая цена",
        button = "Подтвердить покупку",
        busy = busy,
        onSave = onSave,
    ) { price ->
        val difference = price - status.savedKopecks
        PurchaseFormHint(
            when {
                difference > 0L -> "Из доступного остатка спишется ${formatRub(difference)}"
                difference < 0L -> "В доступный остаток вернётся ${formatRub(-difference)}"
                else -> "Покупка полностью оплачена из накоплений"
            },
        )
    }
}

@Composable
internal fun EditPurchaseContributionForm(
    status: PurchaseGoalStatus,
    amount: Long,
    busy: Boolean,
    onSave: (Long) -> Unit,
) {
    PurchaseFormHint(status.goal.title)
    PurchaseAmountForm(amount, "Взнос", "Сохранить взнос", busy, onSave) {}
}

@Composable
private fun PurchaseAmountForm(
    initial: Long?,
    label: String,
    button: String,
    busy: Boolean,
    onSave: (Long) -> Unit,
    preview: @Composable (Long) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.let(AmountInput::fromKopecks).orEmpty()) }
    val amount = AmountInput.parsePositiveKopecks(text)
    TratyAmountTextField(text, { text = it }, Modifier.fillMaxWidth(), label, !busy)
    if (amount != null) preview(amount)
    TratyButton(
        onClick = { if (amount != null) onSave(amount) },
        enabled = !busy && amount != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(button) }
}

@Composable
private fun PurchaseDateField(date: LocalDate?, today: LocalDate, enabled: Boolean, onDate: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    TratyOutlinedButton(
        onClick = { picking = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (date == null) "Выбрать дату покупки" else formatDate(date))
    }
    if (picking) {
        DatePickerModal(
            initialDate = maxOf(date ?: today, today),
            onDateSelected = onDate,
            onDismiss = { picking = false },
            earliestDate = today,
        )
    }
}

@Composable
private fun PurchaseFormHint(text: String) {
    Text(text, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurfaceVariant)
}
