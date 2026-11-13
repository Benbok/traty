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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import ru.my.traty.ui.charts.ChartsScreen
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.home.ConfirmDialog
import ru.my.traty.ui.home.HomeScreen
import ru.my.traty.ui.home.IncomeConfirmDialog
import ru.my.traty.ui.navigation.TratyNavTab
import ru.my.traty.ui.reconcile.ReconcileScreen
import ru.my.traty.ui.settings.SettingsScreen
import ru.my.traty.ui.stats.StatsScreen

/**
 * Корневой контейнер приложения Traty с нижней навигацией по 4 вкладкам:
 * Траты (Home), Графики (Charts), Статистика (Stats), Настройки (Settings).
 */
@Composable
fun TratyRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(TratyNavTab.HOME) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showReconcile by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val categories by vm.categories.collectAsStateWithLifecycle()

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
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
                    TratyNavTab.HOME -> HomeScreen(vm, onOpenReconcile = { showReconcile = true })
                    TratyNavTab.CHARTS -> ChartsScreen()
                    TratyNavTab.STATS -> StatsScreen(vm)
                    TratyNavTab.SETTINGS -> SettingsScreen(
                        vm,
                        onOpenCategories = { showCategories = true },
                        onOpenReconcile = { showReconcile = true },
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

        if (showReconcile) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                ReconcileScreen(onBack = { showReconcile = false })
            }
        }
    }

    vm.draft?.let { d ->
        ConfirmDialog(
            draft = d,
            categories = categories,
            onDismiss = { vm.draft = null },
            onSave = vm::saveDraft,
        )
    }

    vm.incomeDraft?.let { d ->
        IncomeConfirmDialog(
            draft = d,
            onDismiss = { vm.incomeDraft = null },
            onSave = vm::saveIncomeDraft,
        )
    }
}
