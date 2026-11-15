package ru.my.traty.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.capture.ChimeRecordCues
import ru.my.traty.ui.capture.RecordController
import ru.my.traty.ui.capture.RecordState

/** Микрофон ленты: запись, запрос доступа и ошибки записи. */
class HomeMic internal constructor(
    val state: RecordState,
    private val toggle: () -> Unit,
    private val hasPermission: () -> Boolean,
    private val askPermission: () -> Unit,
) {
    /** Нажатие на кнопку: начать или закончить запись, при первом разе спросить доступ. */
    fun onClick() {
        if (state is RecordState.Listening || hasPermission()) toggle() else askPermission()
    }

    /** Начать запись, если она ещё не идёт (кнопка «Записать первую трату» из знакомства). */
    fun start() {
        if (state !is RecordState.Listening) onClick()
    }
}

@Composable
fun rememberHomeMic(vm: MainViewModel): HomeMic {
    val context = LocalContext.current
    val recorder = remember { RecordController({ SpeechEngines.create(context, vm.settings.value) }, ChimeRecordCues()) { vm.onSpokenText(it) } }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    val recordState by recorder.state.collectAsStateWithLifecycle()
    // Под компактной кнопкой нет текста, поэтому ошибку записи показываем всплывающим сообщением
    LaunchedEffect(recordState) {
        (recordState as? RecordState.Error)?.let { vm.message = it.message }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.toggle() else vm.message = "Нужен доступ к микрофону"
    }
    return HomeMic(
        state = recordState,
        toggle = recorder::toggle,
        hasPermission = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        },
        askPermission = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
    )
}
