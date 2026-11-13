package ru.my.traty.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyDeltaBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyInputBar
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.YearMonth

@Composable
fun TratyDesignSystemShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TratyTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.lg),
    ) {
        Text(
            text = "Traty Design System",
            style = TratyTheme.typography.headlineLarge,
            color = TratyTheme.colors.onBackground,
        )

        // Month Picker
        TratyMonthPicker(
            month = YearMonth.of(2026, 11),
            totalKopecks = 1425000,
            onPrev = {},
            onNext = {},
            deltaText = "Прошлый месяц: 12 400 ₽ (+15%)",
        )

        // Amounts
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.md)) {
                Text("Суммы и дельты", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TratyAmountText(amountKopecks = 2500000, size = AmountSize.LARGE)
                    TratyDeltaBadge(current = 2500000, previous = 2000000)
                }
            }
        }

        // Categories & Progress
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("Категории и прогресс", style = TratyTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    TratyCategoryBadge(emoji = "🛒", categoryName = "Продукты")
                    TratyCategoryBadge(emoji = "☕", categoryName = "Кафе")
                    TratyCategoryBadge(emoji = "🚕", categoryName = "Транспорт")
                    TratyCategoryBadge(emoji = "📱", categoryName = "Подписки")
                }
                TratyProgressBar(progress = 0.65f)
            }
        }

        // Buttons
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("Кнопки", style = TratyTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    TratyButton(onClick = {}) { Text("Сохранить") }
                    TratyOutlinedButton(onClick = {}) { Text("Отмена") }
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Добавить",
                        onClick = {},
                        containerColor = TratyTheme.colors.surfaceContainer,
                    )
                }
            }
        }

        // Input Bar
        TratyInputBar(
            value = "кофе 250",
            onValueChange = {},
            onManualClick = {},
            onVoiceClick = {},
            onSendClick = {},
        )

        // Empty State
        TratyEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Все чисто!",
            description = "Трат в этой категории пока нет.",
        )
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
fun TratyDesignSystemShowcasePreviewLight() {
    TratyTheme(darkTheme = false) {
        Surface {
            TratyDesignSystemShowcase()
        }
    }
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
fun TratyDesignSystemShowcasePreviewDark() {
    TratyTheme(darkTheme = true) {
        Surface {
            TratyDesignSystemShowcase()
        }
    }
}
