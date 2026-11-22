package ru.my.traty.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.my.traty.assistant.ProductReportResult
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ProductReportRows(report: ProductReportResult, onShowExpenses: (List<Long>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val previewSize = 5
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        (if (expanded) report.rows else report.rows.take(previewSize)).forEach { row ->
            Column {
                Text(row.name, style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurface)
                Text(row.detail, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    if (row.currentExpenseIds.isNotEmpty()) {
                        TratyTextButton(onClick = { onShowExpenses(row.currentExpenseIds) }) {
                            Text(if (row.previousExpenseIds.isEmpty()) "Покупки" else "Текущие чеки")
                        }
                    }
                    if (row.previousExpenseIds.isNotEmpty()) {
                        TratyTextButton(onClick = { onShowExpenses(row.previousExpenseIds) }) {
                            Text("Предыдущие чеки")
                        }
                    }
                }
            }
        }
        if (report.rows.size > previewSize) {
            TratyTextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Свернуть список" else "Все товары (${report.rows.size})")
            }
        }
        if (report.footer.isNotBlank()) {
            Text(report.footer, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
        }
    }
}
