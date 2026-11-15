package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import java.time.LocalDate

/** Пустая лента «Траты»: нет записей за день, ничего не нашёл поиск или трат пока нет совсем. */
@Composable
fun FeedEmptyState(selectedDate: LocalDate?, searchQuery: String, onShowAll: () -> Unit) {
    when {
        searchQuery.isNotBlank() -> TratyEmptyState(
            icon = Icons.Filled.SearchOff,
            title = "Ничего не нашлось",
            description = "Ни в одном периоде нет записей со словами «${searchQuery.trim()}». Попробуйте другое слово.",
        )
        selectedDate != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = TratyTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TratyEmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "За ${formatDate(selectedDate)} записей нет",
                description = "Нажмите «Показать весь период», чтобы увидеть все записи периода.",
            )
            Spacer(Modifier.height(TratyTheme.spacing.md))
            TratyButton(onClick = onShowAll) {
                Text("Показать весь период")
            }
        }
        else -> TratyEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Трат пока нет",
            description = "Нажмите микрофон внизу и скажите, например: «кофе 250 и такси 400».",
        )
    }
}
