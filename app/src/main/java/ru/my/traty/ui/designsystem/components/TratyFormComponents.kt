package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Крупная сумма по центру карточки: правится прямо здесь, показывается как «20 000,5 руб».
 */
@Composable
fun TratyHeroAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Подпись поля фиксированной ширины в строках настроек/форм.
 */
@Composable
fun TratyRowLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TratyTheme.typography.bodyMedium,
        color = TratyTheme.colors.onSurfaceVariant,
        modifier = modifier.width(116.dp),
    )
}

/**
 * Разделитель между строками формы с отступами слева и справа.
 */
@Composable
fun TratyRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = TratyTheme.colors.borderSubtle,
        modifier = modifier.padding(horizontal = TratyTheme.spacing.lg),
    )
}

/**
 * Строка «название — значение ›», по нажатию открывает выбор.
 */
@Composable
fun TratyPickRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TratyTheme.colors.onSurfaceVariant)
    },
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .tratyClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyRowLabel(label)
        Text(
            value,
            style = TratyTheme.typography.bodyLarge,
            color = if (enabled) TratyTheme.colors.onSurface else TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (enabled) trailing()
    }
}

/**
 * Строка «название — поле ввода» для текстовых полей формы.
 */
@Composable
fun TratyInlineRow(
    label: String,
    value: String,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = TratyTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TratyRowLabel(label)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = TratyTheme.typography.bodyLarge, color = TratyTheme.colors.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                textStyle = TratyTheme.typography.bodyLarge.copy(color = TratyTheme.colors.onSurface),
                cursorBrush = SolidColor(TratyTheme.colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Строка с переключателем в стиле настроек.
 */
@Composable
fun TratySwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TratyTheme.typography.bodyMedium, color = TratyTheme.colors.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(TratyTheme.spacing.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Выбор из списка в нижней шторке; выбранный пункт отмечен галочкой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TratyOptionSheet(
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
                        .tratyClickable { onPick(value) }
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
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = TratyTheme.colors.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(TratyTheme.spacing.lg))
    }
}
