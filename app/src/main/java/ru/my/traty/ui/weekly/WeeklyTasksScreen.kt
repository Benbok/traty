package ru.my.traty.ui.weekly

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.weekly.WeekResult
import ru.my.traty.data.weekly.WeeklyTaskTexts
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateSpan
import ru.my.traty.ui.formatRub

/** «Задания на неделю»: 1–3 задания из ваших трат, свои задания, история и серия выполненных недель. */
@Composable
fun WeeklyTasksScreen(onBack: () -> Unit, vm: WeeklyTasksViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val notify by vm.notifyEnabled.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it) }
        vm.message.value = null
    }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Задания на неделю",
                subtitle = formatDateSpan(ui.weekStart, ui.weekStart.plusDays(6)),
                onBack = onBack,
                actions = {
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Добавить своё задание",
                        onClick = { adding = true },
                        containerColor = TratyTheme.colors.primaryContainer,
                        tint = TratyTheme.colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            item { WeekSummaryCard(ui) }
            if (ui.loaded && ui.current.isEmpty()) {
                item {
                    TratyEmptyState(
                        icon = Icons.Filled.EmojiEvents,
                        title = "Заданий пока нет",
                        description = "Задания появляются по понедельникам, когда в приложении есть хотя бы 3 недели трат. " +
                            "Пока можно добавить своё задание кнопкой «+».",
                    )
                }
            }
            items(ui.current, key = { it.task.id }) { p ->
                WeeklyTaskCard(p, onReplace = vm::replace, onRemove = vm::remove, onCheck = vm::setChecked)
            }
            item { NotifyCard(notify, vm::setNotify) }
            if (ui.history.isNotEmpty()) {
                item {
                    Text("Прошлые недели", style = TratyTheme.typography.titleSmall, modifier = Modifier.padding(top = TratyTheme.spacing.sm))
                }
                items(ui.history, key = { it.weekStart.toEpochDay() }) { HistoryRow(it) }
            }
        }
    }

    if (adding) {
        AddWeeklyTaskDialog(
            categories = categories,
            onDismiss = { adding = false },
            onSave = { type, category, target ->
                vm.add(type, category, target)
                adding = false
            },
        )
    }
}

@Composable
private fun WeekSummaryCard(ui: WeeklyUi) {
    val done = ui.current.count { it.state == ru.my.traty.data.weekly.TaskState.DONE }
    TratyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text(
                if (ui.current.isEmpty()) "На этой неделе заданий нет" else "Выполнено $done из ${ui.current.size}",
                style = TratyTheme.typography.titleMedium,
            )
            Text(
                if (ui.streak > 0) "Серия: ${ui.streak} ${WeeklyTaskTexts.weeksWord(ui.streak)} подряд все задания выполнены"
                else "Выполните все задания недели, чтобы начать серию",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                "Итог подводится в воскресенье вечером.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(r: WeekResult) {
    TratyCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(TratyTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDateSpan(r.weekStart, r.weekStart.plusDays(6)), style = TratyTheme.typography.bodyMedium)
                if (r.savedKopecks > 0) {
                    Text("Сэкономлено ${formatRub(r.savedKopecks)}", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                }
            }
            Text(
                "${if (r.success) "✅" else "◻️"} ${r.done} из ${r.total}",
                style = TratyTheme.typography.labelMedium,
                color = if (r.success) TratyTheme.colors.income else TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotifyCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    TratyCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = onChange).padding(TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Напоминать о заданиях", style = TratyTheme.typography.titleSmall)
                Text(
                    "Новые задания в понедельник утром, итог в воскресенье вечером.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}
