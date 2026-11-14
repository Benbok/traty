package ru.my.traty.ui.reconcile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Сверка баланса поверх вкладок: шаг 1 - калькулятор остатка, шаг 2 - итог. */
@Composable
fun ReconcileScreen(mode: ReconcileMode, onBack: () -> Unit) {
    // Свой ViewModel на каждое открытие: прошлый итог не должен показываться снова.
    val key = rememberSaveable { System.nanoTime().toString() }
    val vm: ReconcileViewModel = viewModel(key = key)
    BackHandler(onBack = onBack)
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mode) { vm.startIn(mode) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TratyHeaderCard(
                title = "Сверка баланса",
                subtitle = "Контроль соответствия с остатком на счетах",
                onBack = onBack,
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
                replacedActualKopecks = vm.replacedActualKopecks.takeIf { vm.redoMode },
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
