package ru.my.traty.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.data.whatsnew.WhatsNewEntry
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.whatsnew.WhatsNewItemRow

/** Что появилось в одной из прошлых версий. Ссылка «Как это работает» раскрывает тему справки на этом же экране. */
@Composable
fun HelpWhatsNewCard(entry: WhatsNewEntry, onOpenTopic: (String) -> Unit, modifier: Modifier = Modifier) {
    TratyCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            Text("Что нового в версии ${entry.version}", style = TratyTheme.typography.titleSmall)
            entry.items.forEach { WhatsNewItemRow(it, onOpenTopic) }
        }
    }
}
