package ru.my.traty.ui.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyHeroAmountField
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOptionSheet
import ru.my.traty.ui.designsystem.components.TratyPickRow
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.components.TratySwitchRow
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/** Что открыто на экране плана: новый план или правка существующего. */
data class PlanEdit(val key: PlanKey?, val amountKopecks: Long, val repeats: Boolean)

private enum class PlanPicker { CATEGORY, SUBCATEGORY }

/**
 * Экран добавления / редактирования плана трат (лимита) по категории или подкатегории.
 * Выполнен в едином дизайне с экраном редактирования трат: Scaffold, TratyHeaderCard,
 * карточка с крупной суммой и бейджем, форма списком полей, умная подсказка и кнопка сохранения в bottomBar.
 */
@Composable
fun EditPlanScreen(
    edit: PlanEdit,
    categories: List<CategoryWithSubs>,
    taken: Set<PlanKey>,
    monthTitle: String,
    hint: (PlanKey) -> Long?,
    onDismiss: () -> Unit,
    onSave: (PlanKey, Long, Boolean) -> Unit,
    onRemove: ((PlanKey) -> Unit)? = null,
) {
    BackHandler(onBack = onDismiss)

    val isNew = edit.key == null

    var categoryName by remember(edit.key) {
        mutableStateOf(
            categories.firstOrNull { it.category.id == edit.key?.categoryId }?.category?.name
                ?: categories.firstOrNull()?.category?.name.orEmpty()
        )
    }
    val selectedCat = categories.firstOrNull { it.category.name == categoryName }
    var subId by remember(edit.key) { mutableStateOf(edit.key?.subcategoryId) }
    val selectedSub = selectedCat?.subs?.firstOrNull { it.id == subId }

    var amount by remember(edit.key) {
        mutableStateOf(if (edit.amountKopecks > 0) AmountInput.fromKopecks(edit.amountKopecks) else "")
    }
    var repeats by remember(edit.key) { mutableStateOf(edit.repeats) }

    var picking by rememberSaveable { mutableStateOf<PlanPicker?>(null) }

    val key = selectedCat?.let { PlanKey(it.category.id, subId?.takeIf { id -> it.subs.any { s -> s.id == id } }) }
    val kopecks = AmountInput.parsePositiveKopecks(amount)
    val suggestion = remember(key) { key?.let(hint) }
    val duplicate = isNew && key != null && key in taken
    val isValid = key != null && kopecks != null

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (isNew) "Новый план" else "План трат",
                subtitle = "На ${monthTitle.lowercase()}",
                onBack = onDismiss,
                actions = {
                    if (!isNew && onRemove != null && edit.key != null) {
                        TratyIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Убрать план с этого месяца",
                            onClick = { onRemove(edit.key) },
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
                        if (key != null && kopecks != null) {
                            onSave(key, kopecks, repeats)
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
                        emoji = selectedCat?.category?.emoji ?: "🎯",
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
                            categoryName,
                            selectedSub?.name,
                            monthTitle,
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
                    if (isNew) {
                        TratyPickRow(
                            label = "Категория",
                            value = "${selectedCat?.category?.emoji.orEmpty()} $categoryName".trim(),
                            enabled = true,
                        ) { picking = PlanPicker.CATEGORY }
                        TratyRowDivider()
                        TratyPickRow(
                            label = "Подкатегория",
                            value = selectedSub?.name ?: "Вся категория",
                            enabled = selectedCat?.subs?.isNotEmpty() == true,
                        ) { picking = PlanPicker.SUBCATEGORY }
                    } else {
                        TratyPickRow(
                            label = "Категория",
                            value = "${selectedCat?.category?.emoji.orEmpty()} $categoryName".trim(),
                            enabled = false,
                        ) {}
                        if (selectedSub != null) {
                            TratyRowDivider()
                            TratyPickRow(
                                label = "Подкатегория",
                                value = selectedSub.name,
                                enabled = false,
                            ) {}
                        }
                    }
                }
            }

            // Параметры плана и подсказка
            TratyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    if (suggestion != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
                        ) {
                            Text(
                                "💡 Средняя за 3 месяца − 10 %: ${formatRub(suggestion)}",
                                style = TratyTheme.typography.bodyMedium,
                                color = TratyTheme.colors.onSurface,
                            )
                            Spacer(Modifier.height(TratyTheme.spacing.xs))
                            TratyTextButton(onClick = { amount = (suggestion / 100).toString() }) {
                                Text("Использовать подсказку")
                            }
                        }
                        TratyRowDivider()
                    }
                    TratySwitchRow(
                        title = "Повторять каждый месяц",
                        subtitle = "План автоматически перенесётся на следующий расчётный период",
                        checked = repeats,
                        onCheckedChange = { repeats = it },
                    )
                }
            }

            if (duplicate) {
                TratyCard(
                    Modifier.fillMaxWidth(),
                    backgroundColor = TratyTheme.colors.surfaceContainer,
                ) {
                    Text(
                        "⚠️ План на эту категорию уже есть, сохранение заменит его сумму.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.warning,
                        modifier = Modifier.padding(TratyTheme.spacing.lg),
                    )
                }
            }

            if (!isNew && onRemove != null && edit.key != null) {
                TratyCard(
                    Modifier.fillMaxWidth(),
                    onClick = { onRemove(edit.key) },
                ) {
                    Text(
                        "Убрать план с этого месяца",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.expense,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(TratyTheme.spacing.lg),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }

    when (picking) {
        PlanPicker.CATEGORY -> TratyOptionSheet(
            title = "Категория",
            options = categories.map { "${it.category.emoji} ${it.category.name}" to it.category.name },
            selected = categoryName,
            onDismiss = { picking = null },
            onPick = { name ->
                categoryName = name
                subId = null
                picking = null
            },
        )
        PlanPicker.SUBCATEGORY -> {
            val subOptions = listOf("Вся категория" to "") +
                (selectedCat?.subs?.sortedWith(compareBy({ it.sortOrder }, { it.name }))?.map { it.name to it.id.toString() } ?: emptyList())
            TratyOptionSheet(
                title = "Подкатегория в «$categoryName»",
                options = subOptions,
                selected = subId?.toString().orEmpty(),
                onDismiss = { picking = null },
                onPick = { value ->
                    subId = value.toLongOrNull()
                    picking = null
                },
            )
        }
        null -> Unit
    }
}
