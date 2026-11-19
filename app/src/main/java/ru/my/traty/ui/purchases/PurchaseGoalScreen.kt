package ru.my.traty.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun PurchaseGoalScreen(onBack: () -> Unit, vm: PurchaseGoalViewModel = viewModel()) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val today by purchaseGoalToday()
    var form by remember { mutableStateOf<PurchaseForm?>(null) }
    val onAction: (PurchaseForm) -> Unit = {
        vm.clearError()
        form = it
    }
    val active = goals.orEmpty().activeGoals(today)
    val completed = goals.orEmpty().filter { it.goal.actualPriceKopecks != null }.asReversed()
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Накопления на покупки",
                onBack = onBack,
                icon = Icons.Filled.Savings,
                actions = {
                    PurchaseGoalAddButton(onClick = { onAction(PurchaseForm.Create) })
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
            contentPadding = PaddingValues(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            if (goals?.isEmpty() == true) {
                item {
                    TratyEmptyState(
                        icon = Icons.Filled.Savings,
                        title = "Покупок в планах пока нет",
                        description = "Добавьте цель, чтобы узнать, сколько откладывать каждый день.",
                    )
                }
            }
            items(active, key = { it.goal.id }) { PurchaseGoalCard(it, today, onAction) }
            if (completed.isNotEmpty()) {
                item { Text("Завершённые", style = TratyTheme.typography.titleSmall) }
                items(completed, key = { it.goal.id }) { status ->
                    CompletedPurchaseCard(status) { onAction(PurchaseForm.HideCompleted(status.goal.id)) }
                }
            }
        }
    }

    form?.let { PurchaseGoalForms(it, goals.orEmpty(), today, vm, onDismiss = { form = null }) }
}
