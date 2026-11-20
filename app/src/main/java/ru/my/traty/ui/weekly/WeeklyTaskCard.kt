package ru.my.traty.ui.weekly

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.data.weekly.TaskProgress
import ru.my.traty.data.weekly.TaskState
import ru.my.traty.data.weekly.WeeklyTask
import ru.my.traty.data.weekly.WeeklyTaskType
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.tratyPressFeedback
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

@Composable
internal fun taskColor(state: TaskState): Color = when (state) {
    TaskState.DONE -> TratyTheme.colors.income
    TaskState.FAILED -> TratyTheme.colors.expense
    TaskState.IN_PROGRESS -> TratyTheme.colors.primary
}

internal fun taskStateTitle(state: TaskState): String = when (state) {
    TaskState.DONE -> "Выполнено"
    TaskState.FAILED -> "Не вышло"
    TaskState.IN_PROGRESS -> "В процессе"
}

/** Карточка задания: формулировка, полоса прогресса, подпись и действия. */
@Composable
fun WeeklyTaskCard(
    p: TaskProgress,
    onReplace: (WeeklyTask) -> Unit,
    onRemove: (WeeklyTask) -> Unit,
    onCheck: (WeeklyTask, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manual = WeeklyTaskType.of(p.task.type) == WeeklyTaskType.REVIEW_SUBSCRIPTIONS
    TratyCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.task.title, style = TratyTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(taskStateTitle(p.state), style = TratyTheme.typography.labelMedium, color = taskColor(p.state))
            }
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            if (manual) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val interactionSource = remember { MutableInteractionSource() }
                    Checkbox(
                        checked = p.task.checked,
                        onCheckedChange = { onCheck(p.task, it) },
                        modifier = Modifier.tratyPressFeedback(interactionSource, TratyTheme.shapes.small),
                        interactionSource = interactionSource,
                    )
                    Text(p.text, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                }
            } else {
                TratyProgressBar(progress = p.fraction, color = taskColor(p.state))
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(taskDetail(p), style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
            TaskActions(p, onReplace, onRemove)
        }
    }
}

private fun taskDetail(p: TaskProgress): String =
    if (p.savedKopecks > 0 && p.state != TaskState.FAILED) "${p.text} · экономия ${formatRub(p.savedKopecks)} против обычного" else p.text

@Composable
private fun TaskActions(p: TaskProgress, onReplace: (WeeklyTask) -> Unit, onRemove: (WeeklyTask) -> Unit) {
    // Итог уже понятен — менять задание незачем
    if (p.state == TaskState.DONE) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (!p.task.custom) TratyTextButton(onClick = { onReplace(p.task) }) { Text("Заменить") }
        TratyTextButton(onClick = { onRemove(p.task) }) { Text("Убрать", color = TratyTheme.colors.onSurfaceVariant) }
    }
}
