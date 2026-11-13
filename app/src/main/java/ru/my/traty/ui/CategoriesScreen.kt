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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Subcategory
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Палитра цветов категории (ARGB). null — цвет по умолчанию. */
private val CategoryColors = listOf(
    0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF4DB6AC, 0xFF64B5F6, 0xFF9575CD, 0xFFF06292,
).map { it.toInt() }

private fun isOther(c: Category) = c.name == DefaultCategories.OTHER

private fun subcategoriesLabel(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "подкатегорий"
        n % 10 == 1 -> "подкатегория"
        n % 10 in 2..4 -> "подкатегории"
        else -> "подкатегорий"
    }
    return if (n == 0) "без подкатегорий" else "$n $word"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val all by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    val active = all.filterNot { it.category.isArchived }
    val archived = all.filter { it.category.isArchived }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Категории", style = TratyTheme.typography.headlineMedium) },
                navigationIcon = {
                    TratyIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TratyTheme.colors.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = TratyTheme.colors.primary,
                contentColor = TratyTheme.colors.onPrimary,
            ) { Icon(Icons.Filled.Add, "Добавить категорию") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            val movable = active.filterNot { isOther(it.category) }
            items(active, key = { it.category.id }) { c ->
                val idx = movable.indexOf(c)
                CategoryRow(
                    item = c,
                    canMoveUp = idx > 0,
                    canMoveDown = idx in 0 until movable.lastIndex,
                    onMove = { up -> vm.move(c.category.id, up) },
                    onClick = { editingId = c.category.id },
                )
                HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        "В архиве",
                        style = TratyTheme.typography.titleSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = TratyTheme.spacing.lg,
                            vertical = TratyTheme.spacing.md,
                        ),
                    )
                }
                items(archived, key = { it.category.id }) { c ->
                    Box(Modifier.alpha(0.55f)) {
                        CategoryRow(c, canMoveUp = false, canMoveDown = false, onMove = {}, onClick = { editingId = c.category.id })
                    }
                    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                }
            }
            item { Spacer(Modifier.padding(bottom = 88.dp)) } // место под FAB
        }
    }

    if (adding) AddCategoryDialog(onDismiss = { adding = false }) { name, emoji ->
        vm.add(name, emoji) { adding = false }
    }

    // Берём актуальную версию из потока, чтобы правки подкатегорий были видны сразу
    all.firstOrNull { it.category.id == editingId }?.let { c ->
        EditCategoryDialog(
            item = c,
            mergeTargets = active.filter { it.category.id != c.category.id },
            vm = vm,
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun CategoryRow(
    item: CategoryWithSubs,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val c = item.category
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.sm, top = TratyTheme.spacing.sm, bottom = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyCategoryBadge(emoji = c.emoji, categoryName = c.name, size = 40.dp)
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
            Text(
                subcategoriesLabel(item.subs.size),
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        if (canMoveUp || canMoveDown) {
            TratyIconButton(Icons.Filled.KeyboardArrowUp, "Выше", onClick = { onMove(true) }, enabled = canMoveUp)
            TratyIconButton(Icons.Filled.KeyboardArrowDown, "Ниже", onClick = { onMove(false) }, enabled = canMoveDown)
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onAdd: (name: String, emoji: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Новая категория", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, shape = TratyTheme.shapes.small)
                OutlinedTextField(emoji, { emoji = it }, label = { Text("Эмодзи") }, singleLine = true, shape = TratyTheme.shapes.small)
            }
        },
        confirmButton = { TratyButton(onClick = { onAdd(name, emoji) }) { Text("Добавить") } },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EditCategoryDialog(
    item: CategoryWithSubs,
    mergeTargets: List<CategoryWithSubs>,
    vm: CategoriesViewModel,
    onDismiss: () -> Unit,
) {
    val c = item.category
    val other = isOther(c)
    var name by remember(c.id) { mutableStateOf(c.name) }
    var emoji by remember(c.id) { mutableStateOf(c.emoji) }
    var color by remember(c.id) { mutableStateOf(c.color) }
    var newSub by remember(c.id) { mutableStateOf("") }
    var merging by remember(c.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text(c.name, style = TratyTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                if (!other) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, shape = TratyTheme.shapes.small)
                }
                OutlinedTextField(emoji, { emoji = it }, label = { Text("Эмодзи") }, singleLine = true, shape = TratyTheme.shapes.small)

                Text("Цвет", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                ColorRow(selected = color, onPick = { color = it })

                HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                Text("Подкатегории", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
                item.subs.sortedBy { it.sortOrder }.forEach { s -> SubcategoryRow(s, vm) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newSub, { newSub = it },
                        label = { Text("Новая подкатегория") },
                        singleLine = true,
                        shape = TratyTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    )
                    TratyIconButton(Icons.Filled.Add, "Добавить подкатегорию", onClick = {
                        if (newSub.isNotBlank()) { vm.addSub(c.id, newSub); newSub = "" }
                    })
                }

                if (!other) {
                    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                    Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                        TratyOutlinedButton(onClick = { vm.setArchived(c.id, !c.isArchived, onDismiss) }) {
                            Text(if (c.isArchived) "Вернуть из архива" else "В архив")
                        }
                        if (mergeTargets.isNotEmpty()) {
                            TratyOutlinedButton(onClick = { merging = true }) { Text("Объединить…") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TratyButton(onClick = { vm.update(c.id, name, emoji, color, onDismiss) }) { Text("Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Закрыть") } },
    )

    if (merging) MergeDialog(source = c, targets = mergeTargets, vm = vm, onDismiss = { merging = false }, onMerged = onDismiss)
}

@Composable
private fun SubcategoryRow(s: Subcategory, vm: CategoriesViewModel) {
    var text by remember(s.id, s.name) { mutableStateOf(s.name) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = TratyTheme.shapes.small,
            modifier = Modifier.weight(1f),
            trailingIcon = if (text.trim() != s.name && text.isNotBlank()) {
                { TratyTextButton(onClick = { vm.renameSub(s.id, text) }) { Text("OK") } }
            } else null,
        )
        TratyIconButton(Icons.Filled.Close, "Удалить «${s.name}»", onClick = { vm.deleteSub(s.id) }, tint = TratyTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun ColorRow(selected: Int?, onPick: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        // «Без цвета»
        Swatch(color = TratyTheme.colors.surfaceContainer, selected = selected == null, label = "∅") { onPick(null) }
        CategoryColors.forEach { argb -> Swatch(Color(argb), selected == argb) { onPick(argb) } }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) TratyTheme.colors.primary else TratyTheme.colors.borderSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun MergeDialog(
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
        title = { Text(if (t == null) "Объединить «${source.name}» с…" else "Объединить?", style = TratyTheme.typography.titleMedium) },
        text = {
            if (t == null) {
                LazyColumn {
                    items(targets, key = { it.category.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { target = c.category }.padding(vertical = TratyTheme.spacing.sm),
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
