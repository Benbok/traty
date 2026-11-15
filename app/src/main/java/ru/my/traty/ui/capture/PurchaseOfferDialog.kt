package ru.my.traty.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.capture.PurchaseForm
import ru.my.traty.capture.PurchaseOffer
import ru.my.traty.data.debt.DebtStatus
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.stats.ModeChip

/**
 * После сохранения траты спрашивает: это доплата за начатую покупку или новая покупка частями.
 * Показывается, только когда [visible] и есть вопрос.
 */
@Composable
fun PurchaseOfferHost(visible: Boolean = true, vm: PurchaseOfferViewModel = viewModel()) {
    val offer by vm.offer.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val current = offer ?: return
    if (!visible) return
    if (current.match != null) {
        AttachPaymentDialog(current, status, error, onYes = { vm.attach(current) }, onNo = { vm.skip(current) })
    } else {
        NewPurchaseDialog(current, error, onSave = { vm.create(current, it) }, onNo = { vm.skip(current) })
    }
}

@Composable
private fun AttachPaymentDialog(offer: PurchaseOffer, status: DebtStatus?, error: String?, onYes: () -> Unit, onNo: () -> Unit) {
    val name = listOfNotNull(offer.title, offer.place).joinToString(", ")
    AlertDialog(
        onDismissRequest = onNo,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Доплата за «${offer.title}»?", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                Text(
                    "${formatRub(offer.amountKopecks)} добавятся к покупке «$name». " + afterPaymentText(offer, status),
                    style = TratyTheme.typography.bodyMedium,
                )
                Text(
                    "Если это другая покупка, нажмите «Нет»: трата уже сохранена как обычная.",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                ErrorText(error)
            }
        },
        confirmButton = { TratyButton(onClick = onYes) { Text("Да, доплата") } },
        dismissButton = { TratyTextButton(onClick = onNo) { Text("Нет") } },
    )
}

/** «Будет оплачено 25 000 из 60 000 ₽.» или «Всего будет оплачено 25 000 ₽.» */
private fun afterPaymentText(offer: PurchaseOffer, status: DebtStatus?): String {
    status ?: return ""
    val paid = status.paidPrincipalKopecks + offer.amountKopecks
    val total = offer.match?.totalKopecks
    return if (total != null) "Будет оплачено ${formatRub(minOf(paid, total))} из ${formatRub(total)}."
    else "Всего будет оплачено ${formatRub(paid)}."
}

@Composable
private fun NewPurchaseDialog(offer: PurchaseOffer, error: String?, onSave: (PurchaseForm) -> Unit, onNo: () -> Unit) {
    var form by remember(offer.key) { mutableStateOf(PurchaseForm.from(offer)) }
    AlertDialog(
        onDismissRequest = onNo,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Покупка частями?", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(newPurchaseIntro(offer), style = TratyTheme.typography.bodyMedium)
                KindChips(form.kind) { form = form.copy(kind = it) }
                PurchaseFields(form) { form = it }
                val preview = form.toDebt(offer)?.let { purchasePreview(it, offer) }
                Text(
                    preview ?: "Укажите цену и число платежей, тогда приложение посчитает график.",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                ErrorText(error)
            }
        },
        confirmButton = { TratyButton(onClick = { onSave(form) }) { Text("Сохранить покупку") } },
        dismissButton = { TratyTextButton(onClick = onNo) { Text("Не надо") } },
    )
}

private fun newPurchaseIntro(offer: PurchaseOffer): String {
    val name = listOfNotNull(offer.title, offer.place).joinToString(", ")
    return if (offer.expenseId != null) {
        "Трата ${formatRub(offer.amountKopecks)} сохранена. Завести покупку «$name», чтобы следующие платежи шли к ней?"
    } else {
        "Сегодня ничего не платили, поэтому трата не записана. Завести покупку «$name», чтобы платежи шли к ней?"
    }
}

@Composable
private fun KindChips(kind: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        ModeChip("Частями", kind == PurchaseTerms.PARTIAL) { onChange(PurchaseTerms.PARTIAL) }
        ModeChip("Рассрочка", kind == PurchaseTerms.INSTALLMENT) { onChange(PurchaseTerms.INSTALLMENT) }
        ModeChip("Кредит", kind == PurchaseTerms.LOAN) { onChange(PurchaseTerms.LOAN) }
    }
}

@Composable
private fun PurchaseFields(form: PurchaseForm, onChange: (PurchaseForm) -> Unit) {
    TratyAmountTextField(
        value = form.total,
        onValueChange = { onChange(form.copy(total = it)) },
        label = if (form.kind == PurchaseTerms.LOAN) "Сумма кредита" else "Полная цена",
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.kind == PurchaseTerms.PARTIAL) return
    OutlinedTextField(
        value = form.count,
        onValueChange = { v -> onChange(form.copy(count = v.filter(Char::isDigit).take(3))) },
        label = { Text(if (form.period == "month") "Сколько месяцев" else "Сколько платежей") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
    )
    if (form.kind == PurchaseTerms.LOAN) {
        OutlinedTextField(
            value = form.rate,
            onValueChange = { v -> onChange(form.copy(rate = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5))) },
            label = { Text("Ставка, % годовых") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        )
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.expense)
}
