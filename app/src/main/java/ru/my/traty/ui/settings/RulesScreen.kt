package ru.my.traty.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.RuleWithNames
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
fun RulesScreen(vm: MainViewModel, onBack: () -> Unit) {
    // Системная «Назад» возвращает на прошлый экран, а не закрывает приложение
    BackHandler(onBack = onBack)
    val rules by vm.rules.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Правила",
                subtitle = "Автоматическая категоризация мест",
                onBack = onBack,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize()
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        ) {
            Text(
                "Когда в тексте или месте встречается слово — автоматически подставляется категория. " +
                    "Правила создаются при сохранении траты с местом.",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))

            if (rules.isEmpty()) {
                Text(
                    "Правил пока нет. Они появятся, когда вы сохраните трату с местом и согласитесь запомнить правило.",
                    style = TratyTheme.typography.bodyMedium,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                items(rules, key = { it.rule.id }) { r ->
                    RuleRow(r, onDelete = { vm.deleteRule(r.rule) })
                    HorizontalDivider(color = TratyTheme.colors.borderSubtle)
                }
            }
        }
    }
}

@Composable
private fun RuleRow(r: RuleWithNames, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "«${r.rule.pattern}» → ${r.categoryEmoji} ${r.categoryName}" +
                    (r.subcategoryName?.let { " / $it" } ?: ""),
                style = TratyTheme.typography.titleSmall,
            )
            Text(
                "Сработало: ${r.rule.hits} раз",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        TratyIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Удалить",
            onClick = onDelete,
            tint = TratyTheme.colors.expense,
        )
    }
}
