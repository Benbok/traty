package ru.my.traty.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.theme.TratyTheme

// Карточки 1–4 знакомства. Тексты согласованы с Arty на макетах (вариант A).

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomePage() = OnboardingPageLayout(
    title = "Траты без таблиц",
    body = "Скажите, что купили, и приложение само запишет трату и разложит её по категориям. " +
        "Всё хранится только на этом телефоне, входить никуда не нужно.",
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xl),
    ) {
        ExampleMic(size = TratyTheme.spacing.huge + TratyTheme.spacing.huge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            ExampleChip("Голосом")
            ExampleChip("Без регистрации")
            ExampleChip("Данные на телефоне")
        }
    }
}

@Composable
internal fun VoicePage() = OnboardingPageLayout(
    title = "Скажите одной фразой",
    body = "Нажмите микрофон внизу ленты и перечислите покупки. Без интернета фраза подождёт и разберётся позже. " +
        "Записывать можно и с рабочего стола: виджетом или плиткой в шторке.",
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExampleBubble("«кофе 250 и такси 400»", mine = false)
        ExampleMic(size = TratyTheme.spacing.xxxl + TratyTheme.spacing.sm)
    }
    Text("Записано", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.primary)
    ExampleSurface {
        ExampleExpenseRow("☕", "Кафе", "кофе", 250_00)
        HorizontalDivider(color = TratyTheme.colors.borderSubtle)
        ExampleExpenseRow("🚕", "Транспорт", "такси", 400_00)
    }
    Text(
        "Пополнение тоже голосом: «зарплата 80 000»",
        style = TratyTheme.typography.bodySmall,
        color = TratyTheme.colors.onSurfaceVariant,
    )
}

@Composable
internal fun PlanPage() = OnboardingPageLayout(
    title = "План и подписки",
    body = "Задайте, сколько хотите тратить по категориям, и полоска покажет, сколько осталось. " +
        "Повторяющиеся платежи приложение найдёт само и напомнит о списании.",
) {
    ExampleSurface {
        Text("План на месяц", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.onSurfaceVariant)
        ExamplePlanRow("Продукты", 12_400_00, 20_000_00)
        ExamplePlanRow("Кафе", 6_800_00, 6_000_00)
    }
    ExampleSurface {
        ExampleExpenseRow("🎬", "Онлайн-кино", "каждый месяц", 399_00)
    }
}

@Composable
internal fun AssistantPage() = OnboardingPageLayout(
    title = "Спросите своими словами",
    body = "На вкладке «ИИ» можно спросить голосом или текстом. Цифры считает приложение по вашим записям, " +
        "ИИ только объясняет их. Для вопросов нужен ключ DeepSeek или YandexGPT, его можно добавить позже.",
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ExampleBubble("Сколько я потратил на такси в октябре?", mine = true)
    }
    ExampleSurface {
        Text("4 650 ₽", style = TratyTheme.typography.largeAmount, color = TratyTheme.colors.onSurface)
        Text(
            "Это 14 поездок, на 1 200 ₽ больше, чем в сентябре.",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurface,
        )
        Text("Что ушло в ИИ", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.primary)
    }
}
