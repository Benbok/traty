package ru.my.traty.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.my.traty.speech.SpeechEngines
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.theme.holidayOn
import ru.my.traty.ui.designsystem.theme.isDark
import java.time.LocalDate

/**
 * Лёгкая прозрачная панель записи поверх любого приложения: запускается плиткой в шторке и виджетом,
 * главное приложение при этом не открывается. Запись начинается сразу.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: CaptureViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = settings.themeMode.isDark()
            // Праздник проверяется при открытии экрана
            val todayHoliday = remember { holidayOn(LocalDate.now()) }
            TratyTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                holiday = todayHoliday.takeIf { settings.holidayDecor },
            ) {
                CaptureScreen(vm, onFinish = ::finish)
            }
        }
    }
}

/** Сколько показывать «Сохранено · Изменить» / «Нет сети», прежде чем закрыть панель. */
private const val AUTO_CLOSE_MS = 2_000L

@Composable
private fun CaptureScreen(vm: CaptureViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val finish by vm.finishRequested.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    val recorder = remember { RecordController({ SpeechEngines.create(context, vm.settings.value) }, ChimeRecordCues()) { vm.onSpokenText(it) } }
    val recordState by recorder.state.collectAsStateWithLifecycle()
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.toggle() else vm.reportError("Нужен доступ к микрофону")
    }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // Запись начинается сразу при первом открытии панели, а не при повороте/пересоздании (иначе поверх диалога подтверждения запустится скрытая запись)
    LaunchedEffect(Unit) {
        if (vm.consumeAutoStart()) {
            if (hasMic()) recorder.toggle() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Таймер закрытия живёт здесь: смена состояния (например, «Изменить») отменяет его сама
    LaunchedEffect(ui) {
        if (ui is CaptureUi.Saved || ui is CaptureUi.Queued) {
            delay(AUTO_CLOSE_MS)
            vm.finish()
        }
    }
    // Панель не закрывается, пока не ответили на «Добавить в Подписки?»
    val offerVm: RecurringOfferViewModel = viewModel()
    val offer by offerVm.offer.collectAsStateWithLifecycle()
    LaunchedEffect(finish, offer) { if (finish && offer == null) onFinish() }
    BackHandler { vm.finish() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.finish() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        RecordPanel(
            ui = ui,
            recordState = recordState,
            busy = busy,
            error = error,
            onMicClick = {
                if (recordState is RecordState.Listening || hasMic()) recorder.toggle()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onEdit = { vm.editSaved() },
            onCancel = { vm.finish() },
            // Нажатия внутри листа не должны закрывать панель
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }

    // После «Сохранить» окно проверки закрывается сразу, даже если панель ещё ждёт ответа про подписку
    (ui as? CaptureUi.Confirm)?.takeIf { !finish }?.let { confirm ->
        EntryConfirmSheet(
            draft = confirm.draft,
            categories = categories,
            notice = confirm.notice,
            onDismiss = { vm.finish() },
            onSave = { vm.save(it) },
        )
    }
    RecurringOfferHost(visible = finish || ui !is CaptureUi.Confirm, vm = offerVm)
}
