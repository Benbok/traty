package ru.my.traty.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.ThemeMode
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Раздел «Тема оформления»: светлая / тёмная / системная и цвета из обоев. */
@Composable
fun ThemeSettingsCard(
    modifier: Modifier = Modifier,
    vm: AppearanceViewModel = viewModel(),
) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    TratyCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = TratyTheme.colors.surface,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Тема оформления", style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                "Выберите светлую, тёмную или системную тему.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeOption(
                        mode = mode,
                        selected = saved.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Цвета из обоев есть только на Android 12 и новее
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(TratyTheme.spacing.md))
                SwitchRow(
                    title = "Цвета из обоев",
                    hint = "Приложение подстроится под обои телефона. Расходы останутся красными, доходы зелёными.",
                    checked = saved.dynamicColor,
                    onChange = vm::setDynamicColor,
                )
            }
            Spacer(Modifier.height(TratyTheme.spacing.md))
            SwitchRow(
                title = "Праздничное оформление",
                hint = "В Новый год, 23 Февраля, 8 Марта, 1 и 9 Мая меняется цвет и значок в шапке.",
                checked = saved.holidayDecor,
                onChange = vm::setHolidayDecor,
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (mode) {
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    }
    TratyCard(
        modifier = modifier.clickable(onClick = onClick),
        backgroundColor = if (selected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, TratyTheme.colors.primary) else null,
        shape = TratyTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TratyTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = mode.title,
                tint = if (selected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                text = mode.title,
                style = TratyTheme.typography.labelMedium,
                color = if (selected) TratyTheme.colors.primary else TratyTheme.colors.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TratyTheme.typography.bodyLarge)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                hint,
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}
