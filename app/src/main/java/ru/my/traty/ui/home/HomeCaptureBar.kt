package ru.my.traty.ui.home

import androidx.compose.foundation.layout.Box
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
 * Низ ленты «Траты»: микрофон по центру, справа от него кнопка «Чек по фото»
 * (только когда в настройках ИИ заполнен Яндекс). Во время записи и разбора камера недоступна.
 */
@Composable
internal fun HomeCaptureBar(mic: HomeMic, busy: Boolean, onError: (String) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        MicButton(
            state = mic.state,
            busy = busy,
            compact = true,
            onClick = mic::onClick,
        )
        ReceiptCameraButton(
            enabled = mic.state is RecordState.Idle && !busy,
            onError = onError,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = TratyTheme.spacing.xl),
        )
    }
}
