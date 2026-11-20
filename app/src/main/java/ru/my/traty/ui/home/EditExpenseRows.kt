package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.data.labels.ExpenseLabelHit
import ru.my.traty.ui.designsystem.components.AmountVisualTransformation
import ru.my.traty.ui.designsystem.components.rememberAmountFieldValue
import ru.my.traty.ui.designsystem.theme.TratyTheme

// Строки и поля экрана «Редактировать трату»

/** Крупная сумма по центру карточки: правится прямо здесь, показывается как «20 000,5 руб». */
@Composable
internal fun AmountField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val (fieldValue, onFieldChange) = rememberAmountFieldValue(value, onValueChange)
    val suffixColor = TratyTheme.colors.onSurfaceVariant
    BasicTextField(
        value = fieldValue,
        onValueChange = onFieldChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TratyTheme.typography.heroAmount.copy(
            color = TratyTheme.colors.onSurface,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(TratyTheme.colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = remember(suffixColor) { AmountVisualTransformation(suffixColor) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun LabelChip(hit: ExpenseLabelHit) {
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .background(TratyTheme.colors.surfaceContainer, TratyTheme.shapes.pill)
            .padding(horizontal = TratyTheme.spacing.md, vertical = TratyTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${hit.label.emoji} ${hit.label.title}",
            style = TratyTheme.typography.labelMedium,
            color = TratyTheme.colors.onSurface,
        )
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        color = TratyTheme.colors.borderSubtle,
        modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
    )
}

/** Строка «название — значение ›», по нажатию открывает выбор. */
@Composable
internal fun PickRow(
    label: String,
    value: String,
    enabled: Boolean,
    trailing: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TratyTheme.colors.onSurfaceVariant)
    },
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        Text(value, style = TratyTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (enabled) trailing()
    }
}

/** Строка «название — поле ввода» для места и заметки. */
@Composable
internal fun InlineRow(label: String, value: String, placeholder: String, enabled: Boolean, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TratyTheme.typography.bodyLarge.copy(color = TratyTheme.colors.onSurface),
                cursorBrush = SolidColor(TratyTheme.colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun RowLabel(text: String) {
    Text(
        text,
        style = TratyTheme.typography.bodyMedium,
        color = TratyTheme.colors.onSurfaceVariant,
        modifier = Modifier.width(116.dp),
    )
}

/** Выбор из списка в нижней шторке; выбранный пункт отмечен галочкой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = TratyTheme.colors.surface,
    ) {
        Text(
            title,
            style = TratyTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(options, key = { it.second }) { (text, value) ->
                val isSelected = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onPick(value) }
                        .padding(horizontal = TratyTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        style = TratyTheme.typography.bodyLarge,
                        color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = TratyTheme.colors.primary)
                }
            }
        }
        Spacer(Modifier.height(TratyTheme.spacing.lg))
    }
}
