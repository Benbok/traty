package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** «1 фраза ждёт разбора», «2 фразы ждут разбора», «5 фраз ждут разбора». */
fun pendingWaitingText(n: Int): String {
    val last = n % 10
    val lastTwo = n % 100
    return when {
        last == 1 && lastTwo != 11 -> "$n фраза ждёт разбора"
        last in 2..4 && lastTwo !in 12..14 -> "$n фразы ждут разбора"
        else -> "$n фраз ждут разбора"
    }
}

/** Карточка очереди над лентой: без [onClick] — просто информация, с ним — открывает проверку. */
@Composable
fun PendingCard(title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    TratyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        backgroundColor = TratyTheme.colors.primaryContainer,
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Text(title, style = TratyTheme.typography.titleSmall, color = TratyTheme.colors.primary)
            if (subtitle != null) {
                Text(subtitle, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            }
        }
    }
}
