package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import java.time.LocalDate
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.ui.purchases.PurchaseGoalAddButton

@Composable
internal fun HomeBrowseHeader(
    selectedDate: LocalDate?,
    period: BillingPeriod,
    periodStartDay: Int,
    totalExpense: Long,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    searchContent: @Composable ColumnScope.() -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onClearDate: () -> Unit,
    onCreatePurchase: () -> Unit,
) {
    TratyHeaderCard(
        title = "Траты",
        subtitle = if (selectedDate != null) "День: ${formatDate(selectedDate)}" else "Учёт расходов и доходов",
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        actions = {
            PurchaseGoalAddButton(onClick = onCreatePurchase)
            TratyIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Поиск",
                onClick = onToggleSearch,
                containerColor = if (searchActive) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                tint = if (searchActive) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        },
        content = if (searchActive) searchContent else null,
    )

    TratyMonthPicker(
        periodLabel = formatPeriod(period, periodStartDay),
        totalKopecks = totalExpense,
        selectedDate = selectedDate,
        onPrev = onPrevious,
        onNext = onNext,
        onDateClick = onPickDate,
        onClearDate = onClearDate,
    )

    if (selectedDate != null) SelectedDayFilter(selectedDate, onPickDate, onClearDate)
}

@Composable
private fun SelectedDayFilter(selectedDate: LocalDate, onPickDate: () -> Unit, onClearDate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputChip(
            selected = true,
            onClick = onPickDate,
            label = { Text("Дата: ${formatDate(selectedDate)}") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Сбросить фильтр",
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = InputChipDefaults.inputChipColors(
                selectedContainerColor = TratyTheme.colors.primaryContainer,
                selectedLabelColor = TratyTheme.colors.primary,
                selectedLeadingIconColor = TratyTheme.colors.primary,
                selectedTrailingIconColor = TratyTheme.colors.primary,
            ),
            shape = TratyTheme.shapes.small,
        )

        TratyTextButton(
            onClick = onClearDate,
        ) {
            Text("Весь период", style = TratyTheme.typography.labelSmall)
        }
    }
}
