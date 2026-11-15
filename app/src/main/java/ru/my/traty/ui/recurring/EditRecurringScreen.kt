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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Recurring
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.data.recurring.RecurrencePeriod
import ru.my.traty.data.recurring.yearlyKopecks
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyHeroAmountField
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyInlineRow
import ru.my.traty.ui.designsystem.components.TratyOptionSheet
import ru.my.traty.ui.designsystem.components.TratyPickRow
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.components.TratySwitchRow
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateWithWeekday
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.DatePickerModal
import java.time.LocalDate

private val UNIT_CHIPS = listOf(
    PeriodUnit.DAY to "Дни",
    PeriodUnit.WEEK to "Недели",
    PeriodUnit.MONTH to "Месяцы",
    PeriodUnit.YEAR to "Годы",
)
private val REMIND_CHIPS = listOf(0 to "Нет", 1 to "За 1 день", 3 to "За 3 дня", 7 to "За 7 дней")

private enum class RecurringPicker { CATEGORY, SUBCATEGORY, DATE }

/**
 * Экран добавления / редактирования подписки или регулярного платежа.
 * Выполнен в едином дизайне с экраном редактирования трат: Scaffold, TratyHeaderCard,
 * карточка с крупной суммой и бейджем, форма списком полей, выбор частоты и напоминаний,
 * кнопка сохранения в bottomBar.
 */
@Composable
fun EditRecurringScreen(
    initial: Recurring,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    BackHandler(onBack = onDismiss)

    val isNew = initial.id == 0L

    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var amount by remember(initial.id) {
        mutableStateOf(if (initial.amountKopecks > 0) AmountInput.fromKopecks(initial.amountKopecks) else "")
    }
    var categoryName by remember(initial.id) {
        mutableStateOf(
            categories.firstOrNull { it.category.id == initial.categoryId }?.category?.name
                ?: categories.firstOrNull()?.category?.name.orEmpty()
        )
    }
    val selectedCat = categories.firstOrNull { it.category.name == categoryName }
    var subcategoryName by remember(initial.id) {
        mutableStateOf(selectedCat?.subs?.firstOrNull { it.id == initial.subcategoryId }?.name.orEmpty())
    }
    var unit by remember(initial.id) { mutableStateOf(PeriodUnit.of(initial.periodUnit)) }
    var every by remember(initial.id) { mutableStateOf(initial.periodEvery.toString()) }
    var dateText by remember(initial.id) { mutableStateOf(LocalDate.ofEpochDay(initial.nextEpochDay).toString()) }
    var remind by remember(initial.id) { mutableStateOf(initial.remindDaysBefore) }
    var varies by remember(initial.id) { mutableStateOf(initial.amountVaries) }

    var picking by rememberSaveable { mutableStateOf<RecurringPicker?>(null) }

    val pickedDate = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val kopecks = AmountInput.parsePositiveKopecks(amount)
    val n = every.toIntOrNull()?.takeIf { it >= 1 }
    val isValid = title.isNotBlank() && kopecks != null && selectedCat != null && pickedDate != null && n != null

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (isNew) "Новый платёж" else "Платёж",
                subtitle = if (isNew) "Подписка или регулярный расход" else title,
                onBack = onDismiss,
                actions = {
                    if (!isNew && onDelete != null) {
                        TratyIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Удалить платёж",
                            onClick = onDelete,
                            containerColor = TratyTheme.colors.expenseContainer,
                            tint = TratyTheme.colors.expense,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                },
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
                TratyButton(
                    onClick = {
                        val subId = selectedCat?.subs?.firstOrNull { it.name == subcategoryName }?.id
                        if (kopecks != null && selectedCat != null && pickedDate != null && n != null) {
                            onSave(
                                initial.copy(
                                    title = title.trim(),
                                    amountKopecks = kopecks,
                                    categoryId = selectedCat.category.id,
                                    subcategoryId = subId,
                                    periodUnit = unit.key,
                                    periodEvery = n,
                                    nextEpochDay = pickedDate.toEpochDay(),
                                    remindDaysBefore = remind,
                                    amountVaries = varies,
                                )
                            )
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
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
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Spacer(Modifier.height(TratyTheme.spacing.xs))

            // Карточка-«чек»: категория, крупная сумма и сводка
            TratyCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(TratyTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                ) {
                    TratyCategoryBadge(
                        emoji = selectedCat?.category?.emoji ?: "💳",
                        categoryName = categoryName,
                        size = 56.dp,
                    )
                    TratyHeroAmountField(
                        value = amount,
                        onValueChange = { amount = it },
                        enabled = true,
                    )
                    Text(
                        listOfNotNull(
                            title.ifBlank { null },
                            categoryName,
                            subcategoryName.ifBlank { null },
                            RecurrencePeriod(unit, n ?: 1).label(),
                            pickedDate?.let(::formatDateWithWeekday),
                        ).joinToString(" · "),
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Поля списком в стиле настроек
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    TratyInlineRow(
                        label = "Название",
                        value = title,
                        placeholder = "Например, Яндекс Плюс",
                        enabled = true,
                    ) { title = it }
                    TratyRowDivider()
                    TratyPickRow(
                        label = "Категория",
                        value = "${selectedCat?.category?.emoji.orEmpty()} $categoryName".trim(),
                        enabled = true,
                    ) { picking = RecurringPicker.CATEGORY }
                    TratyRowDivider()
                    TratyPickRow(
                        label = "Подкатегория",
                        value = subcategoryName.ifBlank { "Нет" },
                        enabled = selectedCat?.subs?.isNotEmpty() == true,
                    ) { picking = RecurringPicker.SUBCATEGORY }
                    TratyRowDivider()
                    TratyPickRow(
                        label = "Следующее",
                        value = pickedDate?.let(::formatDateWithWeekday) ?: dateText,
                        enabled = true,
                        trailing = { Icon(Icons.Filled.DateRange, null, tint = TratyTheme.colors.onSurfaceVariant) },
                    ) { picking = RecurringPicker.DATE }
                }
            }

            // Периодичность списания
            Text(
                "Периодичность",
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
            )
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                    ) {
                        UNIT_CHIPS.forEach { (u, label) ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TratyTheme.colors.primaryContainer,
                                    selectedLabelColor = TratyTheme.colors.primary,
                                ),
                            )
                        }
                    }
                    TratyRowDivider()
                    TratyInlineRow(
                        label = "Интервал",
                        value = every,
                        placeholder = "1 — каждый период",
                        enabled = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    ) { every = it.filter(Char::isDigit).take(3) }
                    TratyRowDivider()
                    TratySwitchRow(
                        title = "Сумма меняется",
                        subtitle = "Например, ЖКУ или связь: перед подтверждением Traty спросит точную сумму",
                        checked = varies,
                        onCheckedChange = { varies = it },
                    )
                }
            }

            // Напоминания
            Text(
                "Напоминания",
                style = TratyTheme.typography.titleSmall,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
            )
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Напоминать каждый день до списания, начиная за:",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.md)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                    ) {
                        REMIND_CHIPS.forEach { (days, label) ->
                            FilterChip(
                                selected = remind == days,
                                onClick = { remind = days },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TratyTheme.colors.primaryContainer,
                                    selectedLabelColor = TratyTheme.colors.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Экономика подписки
            if (!isNew && initial.amountKopecks > 0) {
                Text(
                    "Экономика",
                    style = TratyTheme.typography.titleSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
                )
                TratyCard(Modifier.fillMaxWidth()) {
                    Text(
                        "💡 Отмена этого регулярного платежа сэкономит ${formatRub(initial.yearlyKopecks)} в год",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(TratyTheme.spacing.lg),
                    )
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }

    when (picking) {
        RecurringPicker.CATEGORY -> TratyOptionSheet(
            title = "Категория",
            options = categories.map { "${it.category.emoji} ${it.category.name}" to it.category.name },
            selected = categoryName,
            onDismiss = { picking = null },
            onPick = { name ->
                val subs = categories.firstOrNull { it.category.name == name }?.subNames().orEmpty()
                categoryName = name
                subcategoryName = subcategoryName.takeIf { it in subs }.orEmpty()
                picking = null
            },
        )
        RecurringPicker.SUBCATEGORY -> TratyOptionSheet(
            title = "Подкатегория в «$categoryName»",
            options = listOf("Без подкатегории" to "") +
                selectedCat?.subNames().orEmpty().map { it to it },
            selected = subcategoryName,
            onDismiss = { picking = null },
            onPick = { name ->
                subcategoryName = name
                picking = null
            },
        )
        RecurringPicker.DATE -> DatePickerModal(
            initialDate = pickedDate,
            onDateSelected = {
                dateText = it.toString()
                picking = null
            },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}
