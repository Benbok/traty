package ru.my.traty.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.theme.TratyTheme

private const val MAX_BADGES = 2

/** Плашки переносятся при крупном шрифте; нажатие открывает причины всех меток. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseLabelBadges(labels: List<ExpenseLabelHit>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    var showReasons by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier = modifier
            .clip(TratyTheme.shapes.small)
            .clickable(onClickLabel = "Почему появились метки") { showReasons = true }
            .semantics(mergeDescendants = true) {
                contentDescription = labels.joinToString(", ") { it.label.title }
            },
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        labels.take(MAX_BADGES).forEach { hit -> ExpenseLabelChip(hit.label) }
        if (labels.size > MAX_BADGES) {
            Text(
                "+${labels.size - MAX_BADGES}",
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TratyTheme.spacing.xs, vertical = TratyTheme.spacing.xs),
            )
        }
    }
    if (showReasons) ExpenseLabelReasonsSheet(labels, onDismiss = { showReasons = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseLabelReasonsSheet(labels: List<ExpenseLabelHit>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TratyTheme.colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text("Почему появились метки", style = TratyTheme.typography.titleMedium)
            ExpenseLabelExplanations(labels)
        }
    }
}

/** Единый вид меток и пояснений в карточке покупки и нижней панели. */
@Composable
fun ExpenseLabelExplanations(labels: List<ExpenseLabelHit>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TratyTheme.shapes.small)
            .background(TratyTheme.colors.surfaceContainer)
            .padding(TratyTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        labels.forEach { hit ->
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                ExpenseLabelChip(hit.label, fullTitle = true)
                Text(
                    hit.reason,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
