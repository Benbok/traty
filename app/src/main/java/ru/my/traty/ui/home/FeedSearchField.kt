package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.components.TratyMaterialIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Поле поиска по ленте в шапке «Трат». При открытии сразу получает фокус, чтобы не нужно было
 * нажимать на него ещё раз; кнопка «Найти» на клавиатуре прячет клавиатуру.
 * [mic] — голосовой ввод запроса; [micEnabled] выключает кнопку, пока микрофон занят записью траты.
 */
@Composable
fun FeedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    mic: SearchMic? = null,
    micEnabled: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val micState = mic?.state
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = when (micState) {
                    is RecordState.Listening -> micState.partial.ifBlank { "Слушаю… нажмите, когда закончите" }
                    is RecordState.Recognizing -> "Распознаю…"
                    else -> "Поиск по тратам и товарам…"
                },
                style = TratyTheme.typography.bodyMedium,
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            Row {
                if (mic != null) {
                    TratyMaterialIconButton(onClick = mic::onClick, enabled = micEnabled && micState !is RecordState.Recognizing) {
                        when (micState) {
                            is RecordState.Recognizing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            is RecordState.Listening -> Icon(Icons.Filled.Stop, contentDescription = "Закончить запись", modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Filled.Mic, contentDescription = "Искать голосом", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (query.isNotEmpty()) {
                    TratyMaterialIconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить", modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        shape = TratyTheme.shapes.small,
        textStyle = TratyTheme.typography.bodyMedium,
    )
}
