package ru.my.traty.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateNumeric
import java.time.LocalDate

/**
 * Поле «Дата» для диалогов трат/пополнений: показывает дату как «дд.мм.гггг»
 * и по нажатию открывает календарь [DatePickerModal]. Ручной ввод отключён.
 *
 * [dateText] хранится в ISO-формате (ГГГГ-ММ-ДД) — так его разбирает ViewModel.
 */
@Composable
fun DateField(
    dateText: String,
    onDateTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val date = remember(dateText) { runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() }

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.let(::formatDateNumeric) ?: dateText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Дата") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Выбрать дату") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = TratyTheme.shapes.small,
        )
        // Перехватываем нажатие поверх поля: readOnly-поле само клики не отдаёт
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { showPicker = true },
            )
        }
    }

    if (showPicker) {
        DatePickerModal(
            initialDate = date,
            onDateSelected = { onDateTextChange(it.toString()) },
            onDismiss = { showPicker = false },
        )
    }
}
