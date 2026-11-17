package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.ui.capture.MicButton
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.receipt.ReceiptCameraButton

/**
 * Низ ленты «Траты»: микрофон по центру, справа от него кнопка «Чек»
 * (только когда заполнены все поля DeepSeek и Yandex). Во время записи и разбора камера недоступна.
 */
@Composable
internal fun HomeCaptureBar(mic: HomeMic, busy: Boolean, onError: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        MicButton(
            modifier = Modifier.width(84.dp),
            state = mic.state,
            busy = busy,
            compact = true,
            onClick = mic::onClick,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
        ReceiptCameraButton(
            enabled = mic.state is RecordState.Idle && !busy,
            onError = onError,
            modifier = Modifier
                .padding(end = TratyTheme.spacing.xl),
        )
        }
    }
}
