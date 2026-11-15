package ru.my.traty.ui.weekly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.weekly.TaskState
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Короткая карточка на Тратах: задания текущей недели. Без заданий не показывается. */
@Composable
fun WeeklyTasksHomeCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: WeeklyTasksViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.current.isEmpty()) return
    val done = ui.current.count { it.state == TaskState.DONE }
    val failed = ui.current.count { it.state == TaskState.FAILED }
    TratyCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(TratyTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Задания недели · $done из ${ui.current.size}", style = TratyTheme.typography.titleSmall)
                ui.current.forEach { p ->
                    Text(
                        "${stateMark(p.state)} ${p.task.title}",
                        style = TratyTheme.typography.bodySmall,
                        color = if (p.state == TaskState.FAILED) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
                    )
                }
                if (failed == 0 && ui.streak > 0) {
                    Text("Серия: ${ui.streak}", style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.primary)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть задания",
                tint = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun stateMark(state: TaskState) = when (state) {
    TaskState.DONE -> "✅"
    TaskState.FAILED -> "❌"
    TaskState.IN_PROGRESS -> "⏳"
}
