package ru.my.traty.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import ru.my.traty.ui.charts.ChartsScreen
import ru.my.traty.ui.designsystem.components.TratySnackbar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.capture.EntryConfirmSheet
import ru.my.traty.ui.home.EditExpenseScreen
import ru.my.traty.ui.home.HomeScreen
import ru.my.traty.ui.navigation.TratyNavTab
import ru.my.traty.ui.reconcile.ReconcileMode
import ru.my.traty.ui.reconcile.ReconcileScreen
import ru.my.traty.ui.recurring.RecurringScreen
import ru.my.traty.ui.settings.AiSettingsScreen
import ru.my.traty.ui.settings.RulesScreen
import ru.my.traty.ui.settings.SettingsScreen
import ru.my.traty.ui.settings.TemplatesScreen
import ru.my.traty.ui.stats.StatsScreen

/**
 * Корневой контейнер приложения Traty с нижней навигацией по 4 вкладкам:
 * Траты (Home), Графики (Charts), Статистика (Stats), Настройки (Settings).
 */
@Composable
fun TratyRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(TratyNavTab.HOME) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showRecurring by rememberSaveable { mutableStateOf(false) }
    var showAiSettings by rememberSaveable { mutableStateOf(false) }
    // null - экран сверки закрыт
    var reconcileMode by rememberSaveable { mutableStateOf<ReconcileMode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()

    LaunchedEffect(vm.message) {
        val msg = vm.message ?: return@LaunchedEffect
        val hasUndo = vm.deletedForUndo != null
        val hasEdit = vm.lastSaved != null
        val result = try {
            withTimeoutOrNull(4.seconds) {
                snackbar.showSnackbar(
                    message = msg,
                    actionLabel = when {
                        hasUndo -> "Отменить"
                        hasEdit -> "Изменить"
                        else -> null
                    },
                    withDismissAction = !hasUndo && !hasEdit,
                    duration = SnackbarDuration.Short,
                )
            }
        } catch (e: CancellationException) {
            // Сообщение сменилось — старый эффект не должен стирать состояние нового (lastSaved, message)
            throw e
        } catch (_: Exception) {
            null
        }
        val acted = result == SnackbarResult.ActionPerformed
        when {
            acted && hasUndo -> vm.undoDelete()
            acted && hasEdit -> vm.editLastSaved()
            else -> vm.clearDeletedForUndo()
        }
        vm.lastSaved = null
        vm.message = null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbar,
                    snackbar = { data -> TratySnackbar(data) },
                )
            },
            containerColor = TratyTheme.colors.background,
            bottomBar = {
                NavigationBar(
                    containerColor = TratyTheme.colors.surface,
                    tonalElevation = 2.dp,
                ) {
                    TratyNavTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title, style = TratyTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TratyTheme.colors.primary,
                                selectedTextColor = TratyTheme.colors.primary,
                                indicatorColor = TratyTheme.colors.primaryContainer,
                                unselectedIconColor = TratyTheme.colors.onSurfaceVariant,
                                unselectedTextColor = TratyTheme.colors.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .fillMaxSize(),
            ) {
                when (tab) {
                    TratyNavTab.HOME -> HomeScreen(vm, onOpenReconcile = { reconcileMode = ReconcileMode.NORMAL })
                    TratyNavTab.CHARTS -> ChartsScreen()
                    TratyNavTab.STATS -> StatsScreen(vm)
                    TratyNavTab.SETTINGS -> SettingsScreen(
                        vm,
                        onOpenCategories = { showCategories = true },
                        onOpenReconcile = { reconcileMode = ReconcileMode.NORMAL },
                        onRedoReconcile = { reconcileMode = ReconcileMode.REDO },
                        onOpenTemplates = { showTemplates = true },
                        onOpenRules = { showRules = true },
                        onOpenRecurring = { showRecurring = true },
                        onOpenAi = { showAiSettings = true },
                    )
                }
            }
        }

        // Экран категорий открывается поверх, сохраняя состояние остальных экранов
        if (showCategories) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                CategoriesScreen(onBack = { showCategories = false })
            }
        }

        if (showTemplates) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                TemplatesScreen(vm = vm, onBack = { showTemplates = false })
            }
        }

        if (showRecurring) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                RecurringScreen(onBack = { showRecurring = false })
            }
        }

        if (showRules) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                RulesScreen(vm = vm, onBack = { showRules = false })
            }
        }

        if (showAiSettings) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                AiSettingsScreen(vm = vm, onBack = { showAiSettings = false })
            }
        }

        // Редактирование траты — отдельный экран поверх вкладок, как «Подписки»
        vm.editing?.let { item ->
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                EditExpenseScreen(
                    item = item,
                    categories = categories,
                    labels = labels[item.expense.id].orEmpty(),
                    onDismiss = { vm.editing = null },
                    onSave = { fields -> vm.updateExpense(item, fields) },
                    onDelete = {
                        vm.delete(item.expense)
                        vm.editing = null
                    },
                    onSetCategoryImpulsive = vm::setCategoryImpulsive,
                )
            }
        }

        reconcileMode?.let { mode ->
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                ReconcileScreen(mode = mode, onBack = { reconcileMode = null })
            }
        }
    }

    vm.confirm?.let { d ->
        EntryConfirmSheet(
            draft = d,
            categories = categories,
            notice = vm.confirmNotice,
            onDismiss = vm::dismissConfirm,
            onSave = { vm.saveEntry(it) },
        )
    }

    vm.ruleOffer?.let { offer ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.ruleOffer = null },
            shape = TratyTheme.shapes.large,
            containerColor = TratyTheme.colors.surface,
            title = {
                Text(
                    "Запомнить правило?",
                    style = TratyTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    "Всегда относить «${offer.place}» к категории «${offer.categoryName}»?",
                    style = TratyTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                ru.my.traty.ui.designsystem.components.TratyButton(
                    onClick = {
                        vm.addRule(offer.place, offer.categoryId, offer.subcategoryId)
                        vm.ruleOffer = null
                    },
                ) { Text("Запомнить") }
            },
            dismissButton = {
                ru.my.traty.ui.designsystem.components.TratyTextButton(
                    onClick = { vm.ruleOffer = null },
                ) { Text("Нет") }
            },
        )
    }
}
