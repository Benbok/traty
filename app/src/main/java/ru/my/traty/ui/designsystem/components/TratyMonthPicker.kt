package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatMonth
import java.time.YearMonth

/**
 * Заголовочный компонент переключения месяца и отображения итоговой суммы.
 */
@Composable
fun TratyMonthPicker(
    month: YearMonth,
    totalKopecks: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    deltaText: String? = null,
) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        shape = TratyTheme.shapes.large,
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TratyIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Предыдущий месяц",
                onClick = onPrev,
                containerColor = TratyTheme.colors.surfaceContainer,
                modifier = Modifier.size(40.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = formatMonth(month),
                    style = TratyTheme.typography.titleSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                TratyAmountText(
                    amountKopecks = totalKopecks,
                    size = AmountSize.LARGE,
                    color = TratyTheme.colors.onSurface,
                )
                if (!deltaText.isNullOrBlank()) {
                    Text(
                        text = deltaText,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }

            TratyIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Следующий месяц",
                onClick = onNext,
                containerColor = TratyTheme.colors.surfaceContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
