package ru.my.traty.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Каркас карточки знакомства: картинка-пример сверху, заголовок и пояснение снизу.
 * Прокручивается, чтобы на маленьком экране и с крупным шрифтом ничего не обрезалось.
 */
@Composable
internal fun OnboardingPageLayout(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.xxl, vertical = TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxl, Alignment.CenterVertically),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TratyTheme.shapes.large)
                .background(TratyTheme.colors.primaryContainer)
                .padding(TratyTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            content = illustration,
        )
        Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            Text(title, style = TratyTheme.typography.headlineMedium, color = TratyTheme.colors.onBackground)
            Text(body, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurfaceVariant)
        }
    }
}

/** Точки «какая карточка из скольких». */
@Composable
internal fun OnboardingDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Шаг ${current + 1} из $count" },
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier
                    .size(
                        width = if (active) TratyTheme.spacing.xxl else TratyTheme.spacing.sm,
                        height = TratyTheme.spacing.sm,
                    )
                    .clip(TratyTheme.shapes.extraSmall)
                    .background(if (active) TratyTheme.colors.primary else TratyTheme.colors.surfaceContainerHigh),
            )
        }
    }
}
