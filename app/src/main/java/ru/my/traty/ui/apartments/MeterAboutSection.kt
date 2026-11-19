package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.my.traty.data.apartments.Meter
import ru.my.traty.data.apartments.ResourceType
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIllustrationBadge
import ru.my.traty.ui.designsystem.components.TratyTonalCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun MeterAboutSection(meter: Meter, resource: ResourceType) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
        TratyTonalCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(TratyTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                TratyIllustrationBadge(resource.illustration, size = 44.dp)
                Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    Text(resource.title, style = TratyTheme.typography.titleMedium)
                    MeterStatus(meter.archived)
                }
            }
        }
        MeterAboutCard("Реквизиты счётчика") {
            MeterAboutRow("Номер прибора", meter.serialNumber)
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            MeterAboutRow("Поставщик", meter.provider)
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            MeterAboutRow("Лицевой счёт", meter.account)
        }
        MeterAboutCard("Период учёта") {
            MeterAboutRow("Начало учёта", meter.accountingFrom?.let(HousingDates::format))
            HorizontalDivider(color = TratyTheme.colors.borderSubtle)
            MeterAboutRow("Окончание учёта", meter.accountingTo?.let(HousingDates::format))
        }
    }
}

@Composable
private fun MeterStatus(archived: Boolean) {
    val colors = TratyTheme.colors
    Surface(
        shape = TratyTheme.shapes.small,
        color = if (archived) colors.surfaceContainerHigh else colors.primaryContainer,
    ) {
        Text(
            text = if (archived) "В архиве" else "Действующий",
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
            color = if (archived) colors.onSurfaceVariant else colors.onPrimaryContainer,
            style = TratyTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MeterAboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        HousingSectionTitle(title)
        TratyCard(Modifier.padding(top = TratyTheme.spacing.xs).fillMaxWidth()) {
            Column(Modifier.padding(horizontal = TratyTheme.spacing.md), content = content)
        }
    }
}

@Composable
private fun MeterAboutRow(label: String, value: String?) {
    val filled = value?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        Text(
            text = filled ?: "Не указано",
            modifier = Modifier.weight(1f),
            style = TratyTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            color = if (filled == null) TratyTheme.colors.onSurfaceVariant else TratyTheme.colors.onSurface,
        )
    }
}
