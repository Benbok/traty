package ru.my.traty.ui.reconcile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Сверка баланса поверх вкладок: шаг 1 - калькулятор остатка, шаг 2 - итог. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(onBack: () -> Unit) {
    // Свой ViewModel на каждое открытие: прошлый итог не должен показываться снова.
    val key = rememberSaveable { System.nanoTime().toString() }
    val vm: ReconcileViewModel = viewModel(key = key)
    BackHandler(onBack = onBack)
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Сверка баланса", style = TratyTheme.typography.headlineMedium) },
                navigationIcon = {
                    TratyIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TratyTheme.colors.surface),
            )
        },
    ) { pad ->
        val content = Modifier.padding(pad).fillMaxSize()
        val result = vm.result
        if (result != null) {
            ReconcileResult(
                state = result,
                history = history,
                onDone = onBack,
                onRedo = vm::redo,
                modifier = content,
            )
        } else if (vm.loaded) {
            BalanceCalculator(
                lines = vm.lines,
                totalKopecks = vm.totalKopecks,
                isFirst = vm.isFirst,
                canConfirm = vm.canConfirm,
                saving = vm.savingUi,
                onName = vm::setName,
                onAmount = vm::setAmount,
                onRemove = vm::removeLine,
                onAdd = vm::addLine,
                onConfirm = vm::confirm,
                modifier = content,
            )
        }
    }
}
