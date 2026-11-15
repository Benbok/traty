package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка «Как пользоваться»: справка и повторное знакомство с приложением. */
@Composable
fun OnboardingSettingsCard(onOpenHelp: () -> Unit, onOpen: () -> Unit) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Как пользоваться", style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                "Справка по всем функциям и короткое знакомство с приложением.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                TratyOutlinedButton(onClick = onOpenHelp) { Text("Справка") }
                TratyTextButton(onClick = onOpen) { Text("Показать знакомство") }
            }
        }
    }
}
