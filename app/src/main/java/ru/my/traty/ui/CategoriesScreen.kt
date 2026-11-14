package ru.my.traty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
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
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

private fun isOther(c: Category) = isOtherCategory(c)

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
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Категории",
                subtitle = "Управление категориями и подкатегориями",
                onBack = onBack,
                actions = {
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Добавить категорию",
                        onClick = { adding = true },
                        containerColor = TratyTheme.colors.primaryContainer,
                        tint = TratyTheme.colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                },
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
        LazyColumn(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
        ) {
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

    if (adding || editingId != null) {
        val itemToEdit = all.firstOrNull { it.category.id == editingId }
        Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
            EditCategoryScreen(
                item = itemToEdit,
                mergeTargets = active.filter { it.category.id != itemToEdit?.category?.id },
                vm = vm,
                onDismiss = {
                    adding = false
                    editingId = null
                },
            )
        }
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
