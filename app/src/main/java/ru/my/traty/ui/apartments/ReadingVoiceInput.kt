package ru.my.traty.ui.apartments

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import ru.my.traty.TratyApp
import ru.my.traty.data.AppSettings
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.capture.ChimeRecordCues
import ru.my.traty.ui.capture.RecordController
import ru.my.traty.ui.capture.RecordState
import ru.my.traty.ui.designsystem.components.TratyTonalButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun ReadingVoiceInput(
    enabled: Boolean,
    onText: (String) -> Unit,
    onError: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val textCallback by rememberUpdatedState(onText)
    val errorCallback by rememberUpdatedState(onError)
    val busyCallback by rememberUpdatedState(onBusy)
    val enabledNow by rememberUpdatedState(enabled)
    val settings by produceState<AppSettings?>(null) {
        try { value = (context.applicationContext as TratyApp).container.settings.current() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errorCallback("Не удалось загрузить настройки записи. Откройте форму заново") }
    }
    val settingsNow by rememberUpdatedState(settings)
    val recorder = remember {
        RecordController(
            { SpeechEngines.create(context, requireNotNull(settingsNow)) }, ChimeRecordCues(),
        ) { textCallback(it) }
    }
    val state by recorder.state.collectAsStateWithLifecycle()
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    LaunchedEffect(state) { onBusy(state is RecordState.Listening || state is RecordState.Recognizing) }
    fun toggleRecording() {
        try {
            recorder.toggle()
            busyCallback(recorder.state.value is RecordState.Listening || recorder.state.value is RecordState.Recognizing)
        } catch (_: Exception) {
            recorder.cancel()
            busyCallback(false)
            errorCallback("Не удалось включить микрофон. Попробуйте ещё раз")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enabledNow && settingsNow != null) toggleRecording()
        else if (!granted) errorCallback("Для голосового ввода нужен доступ к микрофону")
    }
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
        TratyTonalButton(
            enabled = enabled && settings != null && state !is RecordState.Recognizing,
            onClick = {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) toggleRecording()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                } catch (_: Exception) { recorder.cancel(); errorCallback("Не удалось включить микрофон. Попробуйте ещё раз") }
            },
        ) {
            Icon(if (state is RecordState.Listening) Icons.Filled.Stop else Icons.Filled.Mic, null)
            Spacer(Modifier.width(TratyTheme.spacing.xs))
            Text(when (state) {
                is RecordState.Listening -> "Закончить запись"
                is RecordState.Recognizing -> "Распознаю…"
                else -> "Сказать показания"
            })
        }
        val caption = when (val s = state) {
            is RecordState.Listening -> s.partial.ifBlank { "Назовите показание. Для нескольких тарифов назовите каждый тариф" }
            is RecordState.Recognizing -> s.partial
            is RecordState.Error -> s.message
            else -> "Например: «123 запятая 05» или «тариф один — 5200, тариф два — 1800»"
        }
        Text(caption, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
    }
}
