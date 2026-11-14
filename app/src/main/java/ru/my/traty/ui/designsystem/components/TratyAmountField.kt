package ru.my.traty.ui.designsystem.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Показывает сырой текст суммы («20000,5») как «20 000,5 руб». Сам текст поля не меняется,
 * поэтому сохранённое значение и разбор остаются прежними; курсор не заходит в «руб».
 * Суффикс рисуется приглушённым цветом [suffixColor].
 */
data class AmountVisualTransformation(val suffixColor: Color = Color.Unspecified) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val f = AmountInput.format(text.text)
        val shown = if (f.text.isEmpty()) {
            AnnotatedString("")
        } else {
            AnnotatedString(
                f.text,
                spanStyles = listOf(AnnotatedString.Range(SpanStyle(color = suffixColor), f.numberLength, f.text.length)),
            )
        }
        return TransformedText(
            shown,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = f.toDisplay[offset.coerceIn(0, f.toDisplay.size - 1)]
                override fun transformedToOriginal(offset: Int) = f.toRaw[offset.coerceIn(0, f.toRaw.size - 1)]
            },
        )
    }
}

/**
 * Состояние поля суммы: каждая правка проходит [AmountInput.sanitize], курсор остаётся
 * на месте, а если текст пришёл снаружи (например, его подставил ИИ), курсор встаёт в конец.
 * Возвращает значение для текстового поля и обработчик его правок.
 */
@Composable
fun rememberAmountFieldValue(
    value: String,
    onValueChange: (String) -> Unit,
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var state by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (state.text == value) state else TextFieldValue(value, TextRange(value.length))
    val onChange: (TextFieldValue) -> Unit = { typed ->
        val clean = AmountInput.sanitize(typed.text, typed.selection.end)
        // Текст не изменился чисткой — оставляем выделение и композицию клавиатуры как есть
        state = if (clean.text == typed.text) typed else TextFieldValue(clean.text, TextRange(clean.cursor))
        if (clean.text != value) onValueChange(clean.text)
    }
    return shown to onChange
}

/**
 * Поле суммы для форм и диалогов: «20 000,0 руб», цифровая клавиатура с запятой,
 * точка и запятая одинаково считаются десятичным разделителем.
 */
@Composable
fun TratyAmountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Сумма",
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    val (fieldValue, onFieldChange) = rememberAmountFieldValue(value, onValueChange)
    val suffixColor = TratyTheme.colors.onSurfaceVariant
    OutlinedTextField(
        value = fieldValue,
        onValueChange = onFieldChange,
        label = { Text(label) },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = remember(suffixColor) { AmountVisualTransformation(suffixColor) },
        modifier = modifier,
        shape = TratyTheme.shapes.small,
    )
}
