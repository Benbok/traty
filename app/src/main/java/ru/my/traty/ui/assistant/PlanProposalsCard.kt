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
import androidx.compose.ui.text.style.TextDecoration
import ru.my.traty.assistant.PlanProposal
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatRub

/** Предложенные строки плана: отметить нужные и добавить в «План трат». Без нажатия план не меняется. */
@Composable
internal fun PlanProposals(m: ChatMessage, onApply: (List<PlanProposal>) -> Unit, onOpenBudget: () -> Unit) {
    var selected by remember(m.id) { mutableStateOf(m.planProposals.indices.toList()) }
    val applied = m.planApplied
    val removing = m.planProposals.firstOrNull()?.remove == true
    Column(
        Modifier
            .fillMaxWidth()
            .background(TratyTheme.colors.surfaceContainer, TratyTheme.shapes.small)
            .padding(vertical = TratyTheme.spacing.xs),
    ) {
        m.planProposals.forEachIndexed { i, p ->
            val checked = i in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = applied == null,
                        role = Role.Checkbox,
                        onValueChange = { on -> selected = if (on) selected + i else selected - i },
                    )
                    .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = applied == null,
                    colors = CheckboxDefaults.colors(checkedColor = TratyTheme.colors.primary),
                )
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("${p.emoji} ${p.title}", style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurface)
                    Text(
                        listOf(p.basis, if (p.remove) null else (p.currentKopecks?.let { "сейчас ${formatRub(it)}" } ?: "плана нет"))
                            .filterNotNull()
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                if (p.remove) {
                    Text(
                        formatRub(p.currentKopecks ?: 0),
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    TratyAmountText(amountKopecks = p.amountKopecks, size = AmountSize.SMALL)
                }
            }
        }
    }
    Spacer(Modifier.height(TratyTheme.spacing.xs))
    if (applied == null) {
        val label = if (removing) "Убрать из плана" else "Добавить в план"
        TratyButton(
            onClick = { onApply(selected.sorted().map { m.planProposals[it] }) },
            enabled = selected.isNotEmpty(),
        ) { Text(if (m.planProposals.size == 1) label else "$label (${selected.size})") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (removing) "✓ Убрано из плана трат: $applied" else "✓ Добавлено в план трат: $applied"),
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.income,
                modifier = Modifier.weight(1f),
            )
            TratyTextButton(onClick = onOpenBudget) { Text("Открыть план", style = TratyTheme.typography.labelMedium) }
        }
    }
}

