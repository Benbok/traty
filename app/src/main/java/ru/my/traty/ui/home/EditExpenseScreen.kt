package ru.my.traty.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.data.isUserEditable
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateNumeric
import ru.my.traty.ui.formatDateWithWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Данные для редактирования одной траты.
 */
data class EditFields(
    val amountText: String,
    val category: String,
    val subcategory: String,
    val place: String,
    val note: String,
    val dateText: String,
)

/**
 * Экран редактирования траты (вариант B из обсуждения дизайна): карточка с крупной суммой и метками,
 * поля списком в стиле настроек, блок «Почему такие метки» с переключателем импульсивной категории.
 * Вёрстка как у «Подписок»: Scaffold, шапка с отступом под статус-бар, «Сохранить» в bottomBar
 * над навигационной панелью и клавиатурой.
 *
 * Переключатель «импульсивная» меняет настройку категории сразу, а не по «Сохранить»:
 * это свойство всех трат категории, а не этой траты.
 */
@Composable
fun EditExpenseScreen(
    item: ExpenseWithCategory,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (EditFields) -> Unit,
    onDelete: () -> Unit,
    onSetCategoryImpulsive: (categoryId: Long, impulsive: Boolean) -> Unit,
    labels: List<ExpenseLabelHit> = emptyList(),
) {
    BackHandler(onBack = onDismiss)

    val e = item.expense
    val editable = e.isUserEditable
    val zone = remember { ZoneId.systemDefault() }
    val date = remember(e.timestamp) {
        Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
    }
    val initialAmount = remember(e.amountKopecks) {
        if (e.amountKopecks % 100 == 0L) (e.amountKopecks / 100).toString()
        else (e.amountKopecks / 100.0).toString()
    }

    var fields by remember(item) {
        mutableStateOf(
            EditFields(
                amountText = initialAmount,
                category = item.categoryName,
                subcategory = item.subcategoryName.orEmpty(),
                place = e.place.orEmpty(),
                note = e.note.orEmpty(),
                dateText = date.toString(),
            )
        )
    }
    var picking by rememberSaveable { mutableStateOf<Picker?>(null) }

    val selectedCat = categories.firstOrNull { it.category.name == fields.category }
    val emoji = selectedCat?.category?.emoji ?: item.categoryEmoji
    val pickedDate = runCatching { LocalDate.parse(fields.dateText) }.getOrNull()

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (editable) "Трата" else "Запись сверки",
                onBack = onDismiss,
                actions = {
                    if (editable) {
                        TratyIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Удалить трату",
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
            if (editable) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
                ) {
                    TratyButton(
                        onClick = { onSave(fields) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Сохранить")
                    }
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

            // Карточка-«чек»: категория, крупная сумма, итог одной строкой и метки
            TratyCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(TratyTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                ) {
                    TratyCategoryBadge(emoji = emoji, categoryName = fields.category, size = 56.dp)
                    AmountField(
                        value = fields.amountText,
                        onValueChange = { fields = fields.copy(amountText = it) },
                        enabled = editable,
                    )
                    Text(
                        listOfNotNull(
                            fields.category,
                            fields.subcategory.ifBlank { null },
                            pickedDate?.let(::formatDateWithWeekday),
                        ).joinToString(" · "),
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (labels.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                            labels.forEach { LabelChip(it) }
                        }
                    }
                }
            }

            if (!editable) {
                Text(
                    "Записи сверки баланса нельзя редактировать. Используйте «Переделать последнюю сверку» в настройках.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }

            // Поля списком: выбор открывается в шторке, место и заметка правятся прямо в строке
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    PickRow("Категория", "$emoji ${fields.category}".trim(), editable) { picking = Picker.CATEGORY }
                    RowDivider()
                    PickRow("Подкатегория", fields.subcategory.ifBlank { "Нет" }, editable && selectedCat != null) {
                        picking = Picker.SUBCATEGORY
                    }
                    RowDivider()
                    InlineRow("Место", fields.place, "Добавить", editable) { fields = fields.copy(place = it) }
                    RowDivider()
                    PickRow(
                        "Дата",
                        pickedDate?.let(::formatDateNumeric) ?: fields.dateText,
                        editable,
                        trailing = { Icon(Icons.Filled.DateRange, null, tint = TratyTheme.colors.onSurfaceVariant) },
                    ) { picking = Picker.DATE }
                    RowDivider()
                    InlineRow("Заметка", fields.note, "Добавить", editable) { fields = fields.copy(note = it) }
                }
            }

            // Метки с пояснениями и флажок категории, от которого зависит ⚡
            if (labels.isNotEmpty() || selectedCat != null) {
                Text(
                    if (labels.isNotEmpty()) "Почему такие метки" else "Наблюдения",
                    style = TratyTheme.typography.labelLarge,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
                )
                TratyCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(TratyTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
                    ) {
                        labels.forEach { hit ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(hit.label.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(TratyTheme.spacing.md))
                                Column {
                                    Text(hit.label.title, style = TratyTheme.typography.titleSmall)
                                    Text(
                                        hit.reason,
                                        style = TratyTheme.typography.bodySmall,
                                        color = TratyTheme.colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (labels.isNotEmpty() && selectedCat != null) {
                            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                        }
                        selectedCat?.let { cat ->
                            val impulsive = cat.category.isImpulsive
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = impulsive,
                                        role = Role.Switch,
                                        onValueChange = { onSetCategoryImpulsive(cat.category.id, it) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Считать «${cat.category.name}» импульсивной",
                                        style = TratyTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "Для всех трат в этой категории: ночные покупки и серии мелких получат ⚡",
                                        style = TratyTheme.typography.bodySmall,
                                        color = TratyTheme.colors.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(TratyTheme.spacing.md))
                                Switch(checked = impulsive, onCheckedChange = null)
                            }
                        }
                    }
                }
            }

            e.rawText?.let { raw ->
                Text(
                    "Сказано: «$raw»",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
                )
            }
            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }

    when (picking) {
        Picker.CATEGORY -> OptionSheet(
            title = "Категория",
            options = categories.map { "${it.category.emoji} ${it.category.name}" to it.category.name },
            selected = fields.category,
            onDismiss = { picking = null },
            onPick = { name ->
                // Подкатегория другой категории не подходит: сбрасываем, если её нет в новой
                val subs = categories.firstOrNull { it.category.name == name }?.subNames().orEmpty()
                fields = fields.copy(
                    category = name,
                    subcategory = fields.subcategory.takeIf { it in subs }.orEmpty(),
                )
                picking = null
            },
        )
        Picker.SUBCATEGORY -> OptionSheet(
            title = "Подкатегория в «${fields.category}»",
            options = listOf("Без подкатегории" to "") +
                selectedCat?.subNames().orEmpty().map { it to it },
            selected = fields.subcategory,
            onDismiss = { picking = null },
            onPick = { name ->
                fields = fields.copy(subcategory = name)
                picking = null
            },
        )
        Picker.DATE -> DatePickerModal(
            initialDate = pickedDate,
            onDateSelected = { fields = fields.copy(dateText = it.toString()) },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

private enum class Picker { CATEGORY, SUBCATEGORY, DATE }

/** Крупная сумма по центру карточки: правится прямо здесь, цифровая клавиатура, «₽» дописывается при показе. */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TratyTheme.typography.heroAmount.copy(
            color = TratyTheme.colors.onSurface,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(TratyTheme.colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = RubSuffix,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Показывает «1240 ₽», не трогая сам текст поля: курсор за «₽» не уходит. */
private val RubSuffix = VisualTransformation { text ->
    val len = text.length
    TransformedText(
        AnnotatedString(text.text + " ₽"),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(len)
        },
    )
}

@Composable
private fun LabelChip(hit: ExpenseLabelHit) {
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .background(TratyTheme.colors.surfaceContainer, TratyTheme.shapes.pill)
            .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${hit.label.emoji} ${hit.label.title}",
            style = TratyTheme.typography.labelMedium,
            color = TratyTheme.colors.onSurface,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = TratyTheme.colors.borderSubtle,
        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
    )
}

/** Строка «название — значение ›», по нажатию открывает выбор. */
@Composable
private fun PickRow(
    label: String,
    value: String,
    enabled: Boolean,
    trailing: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TratyTheme.colors.onSurfaceVariant)
    },
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        Text(value, style = TratyTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (enabled) trailing()
    }
}

/** Строка «название — поле ввода» для места и заметки. */
@Composable
private fun InlineRow(label: String, value: String, placeholder: String, enabled: Boolean, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TratyTheme.typography.bodyLarge.copy(color = TratyTheme.colors.onSurface),
                cursorBrush = SolidColor(TratyTheme.colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text,
        style = TratyTheme.typography.bodyMedium,
        color = TratyTheme.colors.onSurfaceVariant,
        modifier = Modifier.width(116.dp),
    )
}

/** Выбор из списка в нижней шторке; выбранный пункт отмечен галочкой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = TratyTheme.colors.surface,
    ) {
        Text(
            title,
            style = TratyTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(options, key = { it.second }) { (text, value) ->
                val isSelected = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onPick(value) }
                        .padding(horizontal = TratyTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        style = TratyTheme.typography.bodyLarge,
                        color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = TratyTheme.colors.primary)
                }
            }
        }
        Spacer(Modifier.height(TratyTheme.spacing.lg))
    }
}
