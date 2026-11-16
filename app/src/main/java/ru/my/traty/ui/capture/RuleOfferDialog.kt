package ru.my.traty.ui.capture

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.my.traty.capture.RuleOffer
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/** «Запомнить правило?» после сохранения траты с новым местом. Вынесено из TratyRoot без изменений. */
@Composable
fun RuleOfferDialog(offer: RuleOffer, onRemember: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = {
            Text(
                "Запомнить правило?",
                style = TratyTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                "Всегда относить «${offer.place}» к категории «${offer.categoryName}»?",
                style = TratyTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TratyButton(onClick = onRemember) { Text("Запомнить") }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Нет") }
        },
    )
}
