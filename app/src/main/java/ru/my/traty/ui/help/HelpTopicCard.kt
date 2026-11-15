package ru.my.traty.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.help.HelpTopic
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** Тема справки: в свёрнутом виде название и одна строка, по нажатию раскрывается текст. */
@Composable
fun HelpTopicCard(
    topic: HelpTopic,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TratyCard(modifier = modifier.fillMaxWidth(), onClick = onToggle) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                TratyCategoryBadge(emoji = topic.emoji, categoryName = topic.title)
                Column(Modifier.weight(1f)) {
                    Text(topic.title, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.onSurface)
                    Text(
                        topic.summary,
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Раскрыть",
                    tint = TratyTheme.colors.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                HelpTopicBody(topic)
            }
        }
    }
}

@Composable
private fun HelpTopicBody(topic: HelpTopic) {
    Column(
        Modifier.padding(top = TratyTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
    ) {
        topic.body.forEach { line ->
            Text(line, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurface)
        }
        topic.where?.let {
            Text("Где: $it", style = TratyTheme.typography.labelMedium, color = TratyTheme.colors.primary)
        }
    }
}
