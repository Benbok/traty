package ru.my.traty.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.data.help.HelpTopics
import ru.my.traty.data.whatsnew.WhatsNew
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Справка: список тем, одна раскрыта. [initialTopicId] сразу раскрывает нужную тему.
 * [onOpenOnboarding] в шапке снова показывает знакомство с приложением.
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    initialTopicId: String? = null,
) {
    BackHandler(onBack = onBack)
    var expandedId by rememberSaveable { mutableStateOf(initialTopicId) }
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Справка",
                subtitle = "Что умеет приложение и как этим пользоваться",
                onBack = onBack,
                actions = {
                    TratyIconButton(
                        icon = Icons.Filled.Replay,
                        contentDescription = "Показать знакомство",
                        onClick = onOpenOnboarding,
                        tint = TratyTheme.colors.primary,
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize(),
            contentPadding = PaddingValues(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            items(HelpTopics.all, key = { it.id }) { topic ->
                HelpTopicCard(
                    topic = topic,
                    expanded = expandedId == topic.id,
                    onToggle = { expandedId = if (expandedId == topic.id) null else topic.id },
                )
            }
            items(WhatsNew.entries, key = { "new-${it.version}" }) { entry ->
                HelpWhatsNewCard(entry, onOpenTopic = { expandedId = it })
            }
        }
    }
}
