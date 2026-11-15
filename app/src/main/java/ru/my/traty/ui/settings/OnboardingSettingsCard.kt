package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Карточка «Как пользоваться»: снова открыть знакомство с приложением. */
@Composable
fun OnboardingSettingsCard(onOpen: () -> Unit) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Как пользоваться", style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                "Короткое знакомство: голосовой ввод, план трат, подписки и ИИ-помощник.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            TratyOutlinedButton(onClick = onOpen) { Text("Показать знакомство") }
        }
    }
}
