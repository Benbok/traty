package ru.my.traty.ui.charts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.my.traty.ui.charts.model.CashFlowOperation
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatTime
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CashFlowDayOperations(
    day: LocalDate,
    operations: List<CashFlowOperation>,
    colors: List<Color>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TratyTheme.colors.surface,
    ) {
        Text(
            "Операции за ${formatDate(day)}",
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
            style = TratyTheme.typography.titleMedium,
        )
        LazyColumn(
            contentPadding = PaddingValues(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            items(operations) { operation ->
                Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxs)) {
                    CashFlowAmount(operation.title, operation.amount, colors[operation.seriesIndex])
                    Text(
                        formatTime(operation.timestamp),
                        style = TratyTheme.typography.labelSmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
