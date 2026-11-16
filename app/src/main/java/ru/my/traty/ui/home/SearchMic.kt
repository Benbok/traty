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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.AppSettings
import ru.my.traty.data.search.SearchVoice
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.capture.ChimeRecordCues
import ru.my.traty.ui.capture.RecordController
import ru.my.traty.ui.capture.RecordState

/**
 * Микрофон в поле поиска: надиктованные слова попадают в строку поиска ([SearchVoice.clean]).
 * Без ИИ и без разбора на траты: это только текст запроса, ничего не записывается и никуда не отправляется.
 */
class SearchMic internal constructor(
    val state: RecordState,
    private val toggle: () -> Unit,
    private val hasPermission: () -> Boolean,
    private val askPermission: () -> Unit,
) {
    /** Нажатие на кнопку: начать или закончить запись, при первом разе спросить доступ к микрофону. */
    fun onClick() {
        if (state is RecordState.Listening || hasPermission()) toggle() else askPermission()
    }
}

/** [settings] читаются при каждом запуске записи (движок речи зависит от настроек). */
@Composable
fun rememberSearchMic(
    settings: () -> AppSettings,
    onQuery: (String) -> Unit,
    onError: (String) -> Unit,
): SearchMic {
    val context = LocalContext.current
    val currentOnQuery by rememberUpdatedState(onQuery)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember {
        RecordController({ SpeechEngines.create(context, settings()) }, ChimeRecordCues()) { spoken ->
            val query = SearchVoice.clean(spoken)
            if (query.isNotEmpty()) currentOnQuery(query)
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    val state by recorder.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? RecordState.Error)?.let { currentOnError(it.message) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.toggle() else currentOnError("Нужен доступ к микрофону")
    }
    return SearchMic(
        state = state,
        toggle = recorder::toggle,
        hasPermission = { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
        askPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) },
    )
}
