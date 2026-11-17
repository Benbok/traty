package ru.my.traty.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun DataSettingsCard(
    onOpenApartments: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRules: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        Text(
            "Мои данные",
            style = TratyTheme.typography.titleSmall,
            color = TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = TratyTheme.spacing.sm),
        )
        TratyCard(Modifier.fillMaxWidth()) {
            Column {
                DataSettingsRow(Icons.Filled.Apartment, "Квартиры", "Счётчики, показания и потребление", onOpenApartments)
                TratyRowDivider()
                DataSettingsRow(Icons.Filled.Category, "Категории", "Категории и подкатегории трат", onOpenCategories)
                TratyRowDivider()
                DataSettingsRow(Icons.AutoMirrored.Filled.Rule, "Правила", "Автоматическая категоризация трат", onOpenRules)
            }
        }
    }
}

@Composable
private fun DataSettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(TratyTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = TratyTheme.colors.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
            Text(title, style = TratyTheme.typography.titleMedium, color = TratyTheme.colors.onSurface)
            Text(subtitle, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TratyTheme.colors.onSurfaceVariant)
    }
}
