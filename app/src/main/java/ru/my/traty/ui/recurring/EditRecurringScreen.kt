package ru.my.traty.ui.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.home.DateField
import ru.my.traty.ui.home.DiscardChangesDialog
import java.time.LocalDate

/**
 * Правка платежа на полном экране вместо диалога: поля не прячутся за клавиатурой, а кнопка
 * «Сохранить» всегда внизу. Если что-то поменяли, «Назад» спрашивает, сохранять ли.
 */
@Composable
fun EditRecurringScreen(
    initial: Recurring,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
) {
    val isNew = initial.id == 0L
    val initialAmount = remember(initial) {
        if (initial.amountKopecks > 0) AmountInput.fromKopecks(initial.amountKopecks) else ""
    }
    val initialCategory = remember(initial, categories) {
        categories.firstOrNull { it.category.id == initial.categoryId }?.category?.name
            ?: categories.firstOrNull()?.category?.name.orEmpty()
    }
    val initialDate = remember(initial) { LocalDate.ofEpochDay(initial.nextEpochDay).toString() }

    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(initialAmount) }
    var categoryName by remember { mutableStateOf(initialCategory) }
    var unit by remember { mutableStateOf(PeriodUnit.of(initial.periodUnit)) }
    var every by remember { mutableStateOf(initial.periodEvery.toString()) }
    var dateText by remember { mutableStateOf(initialDate) }
    var remind by remember { mutableStateOf(initial.remindDaysBefore) }
    var varies by remember { mutableStateOf(initial.amountVaries) }
    var askDiscard by remember { mutableStateOf(false) }

    val changed = title != initial.title || amount != initialAmount || categoryName != initialCategory ||
        unit != PeriodUnit.of(initial.periodUnit) || every != initial.periodEvery.toString() ||
        dateText != initialDate || remind != initial.remindDaysBefore || varies != initial.amountVaries

    // Сохраняет, если все поля заполнены правильно; иначе остаёмся на экране
    val save: () -> Unit = {
        val kopecks = AmountInput.parsePositiveKopecks(amount)
        val cat = categories.firstOrNull { it.category.name == categoryName }
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
        val n = every.toIntOrNull()?.takeIf { it >= 1 }
        if (title.isNotBlank() && kopecks != null && cat != null && date != null && n != null) {
            onSave(
                initial.copy(
                    title = title.trim(),
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
    }
    val leave: () -> Unit = { if (changed) { askDiscard = true } else { onDismiss() } }
    BackHandler(onBack = leave)

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (isNew) "Новый платёж" else "Платёж",
                onBack = leave,
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            ) {
                TratyButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isNew) "Добавить" else "Сохранить")
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название (напр. Яндекс Плюс)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            )
            TratyAmountTextField(value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth())
            CategoryPicker(categoryName, categories, null) { categoryName = it }
            Text("Как часто списывается", style = TratyTheme.typography.labelLarge, color = TratyTheme.colors.onSurfaceVariant)
            ChipRow {
                UNIT_CHIPS.forEach { (u, label) ->
                    FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(label) })
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
            FilterChip(selected = varies, onClick = { varies = !varies }, label = { Text("Сумма меняется (коммуналка)") })
            DateField(dateText = dateText, onDateTextChange = { dateText = it })
            Text(
                "Напоминать каждый день до списания, начиная за:",
                style = TratyTheme.typography.labelLarge,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            ChipRow {
                REMIND_CHIPS.forEach { (days, label) ->
                    FilterChip(selected = remind == days, onClick = { remind = days }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }

    if (askDiscard) {
        DiscardChangesDialog(
            message = "Вы поменяли платёж, но ещё не сохранили его.",
            onSave = {
                askDiscard = false
                save()
            },
            onDiscard = {
                askDiscard = false
                onDismiss()
            },
            onStay = { askDiscard = false },
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) { content() }
}
