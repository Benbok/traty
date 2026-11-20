package ru.my.traty.ui.recurring

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Recurring
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyAmountTextField
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyFilterChip
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.home.DateField
import java.time.LocalDate

/** Диалоги экрана «Подписки»: правка платежа и «Сколько списалось?». */
@Composable
internal fun RecurringEditDialog(
    initial: Recurring,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
) {
    val isNew = initial.id == 0L
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amountKopecks > 0) AmountInput.fromKopecks(initial.amountKopecks) else "") }
    var categoryName by remember {
        mutableStateOf(categories.firstOrNull { it.category.id == initial.categoryId }?.category?.name ?: categories.firstOrNull()?.category?.name.orEmpty())
    }
    var unit by remember { mutableStateOf(PeriodUnit.of(initial.periodUnit)) }
    var every by remember { mutableStateOf(initial.periodEvery.toString()) }
    var dateText by remember { mutableStateOf(LocalDate.ofEpochDay(initial.nextEpochDay).toString()) }
    var remind by remember { mutableStateOf(initial.remindDaysBefore) }
    var varies by remember { mutableStateOf(initial.amountVaries) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text(if (isNew) "Новый платёж" else "Платёж", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (напр. Яндекс Плюс)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                TratyAmountTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(categoryName, categories, null) { categoryName = it }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    UNIT_CHIPS.forEach { (u, label) ->
                        TratyFilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = every,
                    onValueChange = { every = it.filter(Char::isDigit).take(3) },
                    label = { Text("Раз в … (1 — каждый период)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                TratyFilterChip(
                    selected = varies,
                    onClick = { varies = !varies },
                    label = { Text("Сумма меняется (коммуналка)") },
                )
                DateField(dateText = dateText, onDateTextChange = { dateText = it })
                Text("Напоминать каждый день до списания, начиная за:", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    REMIND_CHIPS.forEach { (days, label) ->
                        TratyFilterChip(selected = remind == days, onClick = { remind = days }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    val kopecks = AmountInput.parsePositiveKopecks(amount)
                    val cat = categories.firstOrNull { it.category.name == categoryName }
                    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val n = every.toIntOrNull()?.takeIf { it >= 1 }
                    if (title.isNotBlank() && kopecks != null && cat != null && date != null && n != null) {
                        onSave(
                            initial.copy(
                                title = title,
                                amountKopecks = kopecks,
                                categoryId = cat.category.id,
                                subcategoryId = if (cat.category.id == initial.categoryId) initial.subcategoryId else null,
                                periodUnit = unit.key,
                                periodEvery = n,
                                nextEpochDay = date.toEpochDay(),
                                remindDaysBefore = remind,
                                amountVaries = varies,
                            )
                        )
                    }
                },
            ) { Text(if (isNew) "Добавить" else "Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

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

private val UNIT_CHIPS = listOf(
    PeriodUnit.DAY to "Дни",
    PeriodUnit.WEEK to "Недели",
    PeriodUnit.MONTH to "Месяцы",
    PeriodUnit.YEAR to "Годы",
)
private val REMIND_CHIPS = listOf(0 to "Нет", 1 to "За 1 день", 3 to "За 3 дня", 7 to "За 7 дней")
