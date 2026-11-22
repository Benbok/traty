package ru.my.traty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Subcategory
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyInlineRow
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.components.TratyRowLabel
import ru.my.traty.ui.designsystem.components.TratySwitchRow
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Палитра цветов категории (ARGB). null — цвет по умолчанию. */
val CategoryColors = listOf(
    0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF4DB6AC, 0xFF64B5F6, 0xFF9575CD, 0xFFF06292,
).map { it.toInt() }

fun isOtherCategory(c: Category) = c.name == DefaultCategories.OTHER

/**
 * Экран добавления / редактирования категории трат.
 * Выполнен в едином дизайне с экраном редактирования трат: Scaffold, TratyHeaderCard,
 * карточка-превью с бейджем, форма списком полей, управление подкатегориями и кнопка сохранения в bottomBar.
 */
@Composable
fun EditCategoryScreen(
    item: CategoryWithSubs?,
    mergeTargets: List<CategoryWithSubs>,
    vm: CategoriesViewModel,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val isNew = item == null
    val c = item?.category
    val other = c?.let { isOtherCategory(it) } ?: false

    var name by remember(c?.id) { mutableStateOf(c?.name.orEmpty()) }
    var emoji by remember(c?.id) { mutableStateOf(c?.emoji.orEmpty()) }
    var color by remember(c?.id) { mutableStateOf(c?.color) }
    var impulsive by remember(c?.id) { mutableStateOf(c?.isImpulsive ?: false) }
    var newSub by remember(c?.id) { mutableStateOf("") }
    var merging by remember(c?.id) { mutableStateOf(false) }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = if (isNew) "Новая категория" else (c?.name ?: "Категория"),
                subtitle = if (isNew) "Создание категории" else "Редактирование категории",
                onBack = onDismiss,
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
                        if (isNew) {
                            vm.add(name.trim(), emoji.trim(), color, impulsive, onDismiss)
                        } else if (c != null) {
                            vm.update(c.id, name.trim(), emoji.trim(), color, impulsive, onDismiss)
                        }
                    },
                    enabled = name.isNotBlank(),
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

            // Карточка-превью: крупный бейдж, название и статус импульсивности
            TratyCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(TratyTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                ) {
                    TratyCategoryBadge(
                        emoji = emoji.ifBlank { "🏷️" },
                        categoryName = name.ifBlank { "Новая категория" },
                        size = 56.dp,
                    )
                    Text(
                        name.ifBlank { if (isNew) "Новая категория" else c?.name.orEmpty() },
                        style = TratyTheme.typography.headlineMedium,
                        color = TratyTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (impulsive) "⚡ Импульсивная категория" else "Обычная категория",
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
                        value = name,
                        placeholder = "Например, Кафе",
                        enabled = !other,
                    ) { name = it }
                    TratyRowDivider()
                    TratyInlineRow(
                        label = "Эмодзи",
                        value = emoji,
                        placeholder = "Например, ☕",
                        enabled = true,
                    ) { emoji = it }
                    TratyRowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = TratyTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TratyRowLabel("Цвет")
                        CategoryColorPalette(selected = color, onPick = { color = it })
                    }
                    TratyRowDivider()
                    TratySwitchRow(
                        title = "Импульсивные траты ⚡",
                        subtitle = "Все покупки в этой категории получат метку ⚡",
                        checked = impulsive,
                        onCheckedChange = { impulsive = it },
                    )
                }
            }

            // Блок подкатегорий (при редактировании существующей категории)
            if (!isNew && item != null && c != null) {
                Text(
                    "Подкатегории",
                    style = TratyTheme.typography.titleSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
                )
                TratyCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(TratyTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                    ) {
                        item.subs.sortedBy { it.sortOrder }.forEach { s ->
                            CategorySubcategoryRow(s, vm)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newSub,
                                onValueChange = { newSub = it },
                                label = { Text("Новая подкатегория") },
                                singleLine = true,
                                shape = TratyTheme.shapes.small,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(TratyTheme.spacing.sm))
                            TratyIconButton(
                                icon = Icons.Filled.Add,
                                contentDescription = "Добавить подкатегорию",
                                onClick = {
                                    if (newSub.isNotBlank()) {
                                        vm.addSub(c.id, newSub.trim())
                                        newSub = ""
                                    }
                                },
                            )
                        }
                    }
                }

                // Дополнительные действия: архив и объединение
                if (!other) {
                    Text(
                        "Действия",
                        style = TratyTheme.typography.titleSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs),
                    )
                    TratyCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(TratyTheme.spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                            ) {
                                TratyOutlinedButton(
                                    onClick = { vm.setArchived(c.id, !c.isArchived, onDismiss) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (c.isArchived) "Вернуть из архива" else "В архив")
                                }
                                if (mergeTargets.isNotEmpty()) {
                                    TratyOutlinedButton(
                                        onClick = { merging = true },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Объединить…")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.lg))
        }
    }

    if (merging && c != null) {
        CategoryMergeDialog(
            source = c,
            targets = mergeTargets,
            vm = vm,
            onDismiss = { merging = false },
            onMerged = onDismiss,
        )
    }
}

@Composable
private fun CategorySubcategoryRow(s: Subcategory, vm: CategoriesViewModel) {
    var text by remember(s.id, s.name) { mutableStateOf(s.name) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = TratyTheme.shapes.small,
            modifier = Modifier.weight(1f),
            trailingIcon = if (text.trim() != s.name && text.isNotBlank()) {
                { TratyTextButton(onClick = { vm.renameSub(s.id, text.trim()) }) { Text("OK") } }
            } else null,
        )
        TratyIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Удалить «${s.name}»",
            onClick = { vm.deleteSub(s.id) },
            tint = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
fun CategoryColorPalette(selected: Int?, onPick: (Int?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // «Без цвета»
        ColorSwatch(color = TratyTheme.colors.surfaceContainer, selected = selected == null, label = "∅") { onPick(null) }
        CategoryColors.forEach { argb -> ColorSwatch(Color(argb), selected == argb) { onPick(argb) } }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) TratyTheme.colors.primary else TratyTheme.colors.borderSubtle,
                CircleShape,
            )
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun CategoryMergeDialog(
    source: Category,
    targets: List<CategoryWithSubs>,
    vm: CategoriesViewModel,
    onDismiss: () -> Unit,
    onMerged: () -> Unit,
) {
    var target by remember { mutableStateOf<Category?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(source.id) { count = vm.expenseCount(source.id) }

    val t = target
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                if (t == null) "Объединить «${source.name}» с…" else "Объединить?",
                style = TratyTheme.typography.titleMedium,
            )
        },
        text = {
            if (t == null) {
                LazyColumn {
                    items(targets, key = { it.category.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { target = c.category }
                                .padding(vertical = TratyTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${c.category.emoji} ${c.category.name}", style = TratyTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text(
                    "Все траты (${count ?: "…"}) и подкатегории перейдут в «${t.name}». " +
                        "Категория «${source.name}» будет удалена.",
                    style = TratyTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            if (t != null) {
                TratyButton(onClick = { vm.merge(source.id, t.id) { onDismiss(); onMerged() } }) { Text("Объединить") }
            }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
