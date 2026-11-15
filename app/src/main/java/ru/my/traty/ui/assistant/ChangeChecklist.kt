package ru.my.traty.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Строка предложенного изменения: название, что произойдёт и сумма справа. */
internal data class ChangeRow(val title: String, val summary: String, val amountKopecks: Long?, val preselected: Boolean = true)

/** Подписи кнопки и итога для [ChangeChecklist]. */
internal data class ChangeTexts(
    val apply: String,
    val done: String,
    val open: String,
    /** Подсказка под итогом, например «отпишитесь и в самом сервисе». */
    val afterNote: String? = null,
    /** Кнопка «Вернуть» после выполнения; null — вернуть нельзя. */
    val undo: String? = null,
)

/**
 * Изменения, которые предложил ИИ: отметить нужные и подтвердить. Без нажатия ничего не меняется.
 * [applied] — сколько уже выполнено; null — ещё не нажимали.
 */
@Composable
internal fun ChangeChecklist(
    key: Long,
    rows: List<ChangeRow>,
    applied: Int?,
    texts: ChangeTexts,
    onApply: (List<Int>) -> Unit,
    onOpen: () -> Unit,
    /** Изменения уже возвращены кнопкой «Вернуть». */
    undone: Boolean = false,
    onUndo: (() -> Unit)? = null,
) {
    var selected by remember(key) { mutableStateOf(rows.indices.filter { rows[it].preselected }) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(TratyTheme.colors.surfaceContainer, TratyTheme.shapes.small)
            .padding(vertical = TratyTheme.spacing.xs),
    ) {
        rows.forEachIndexed { i, row ->
            ChangeRowItem(row, checked = i in selected, enabled = applied == null) { on ->
                selected = if (on) selected + i else selected - i
            }
        }
    }
    Spacer(Modifier.height(TratyTheme.spacing.xs))
    if (applied == null) {
        TratyButton(onClick = { onApply(selected.sorted()) }, enabled = selected.isNotEmpty()) {
            Text(if (rows.size == 1) texts.apply else "${texts.apply} (${selected.size})")
        }
    } else if (undone) {
        Text("↩ Возвращено", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ${texts.done}: $applied", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.income, modifier = Modifier.weight(1f))
            if (texts.undo != null && onUndo != null) {
                TratyTextButton(onClick = onUndo) { Text(texts.undo, style = TratyTheme.typography.labelMedium) }
            }
            if (texts.open.isNotEmpty()) {
                TratyTextButton(onClick = onOpen) { Text(texts.open, style = TratyTheme.typography.labelMedium) }
            }
        }
        texts.afterNote?.let {
            Text(it, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChangeRowItem(row: ChangeRow, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = TratyTheme.colors.primary),
        )
        Spacer(Modifier.width(TratyTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurface)
            Text(row.summary, style = TratyTheme.typography.labelSmall, color = TratyTheme.colors.onSurfaceVariant)
        }
        row.amountKopecks?.let { TratyAmountText(amountKopecks = it, size = AmountSize.SMALL) }
    }
}
