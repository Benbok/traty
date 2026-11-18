package ru.my.traty.ui.apartments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.stats.ModeChip
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubmissionMonthPicker(
    initial: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    title: String = "За какой месяц переданы показания",
) {
    var year by rememberSaveable { mutableStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TratyTheme.colors.surface,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TratyIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий год", { year-- }, enabled = year > 1)
                    Text(year.toString(), style = TratyTheme.typography.titleMedium)
                    TratyIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий год", { year++ }, enabled = year < 9999)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                    Month.entries.forEach { month ->
                        val option = YearMonth.of(year, month.value)
                        ModeChip(
                            month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")),
                            selected = option == initial,
                        ) { onPick(option) }
                    }
                }
            }
        },
        confirmButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
