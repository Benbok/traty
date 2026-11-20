package ru.my.traty.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.VoiceEngine
import ru.my.traty.speech.ModelState
import ru.my.traty.ui.designsystem.components.TratyRadioButton
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import kotlin.math.roundToInt

/** Раздел «Голос»: выбор движка распознавания и модель GigaAM (скачать / удалить). */
@Composable
fun VoiceSettingsCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val model by vm.modelState.collectAsStateWithLifecycle()
    // Файл могли положить вручную, пока экран был закрыт
    LaunchedEffect(Unit) { vm.refreshModel() }

    TratyCard(modifier = modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Голос", style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            VoiceEngine.entries.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setVoiceEngine(e) }
                        .padding(vertical = TratyTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TratyRadioButton(selected = settings.voiceEngine == e, onClick = { vm.setVoiceEngine(e) })
                    Spacer(Modifier.width(TratyTheme.spacing.sm))
                    Text(e.title, style = TratyTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            when (val m = model) {
                is ModelState.Missing -> {
                    Hint("Модель GigaAM не скачана (≈305 МБ, лучше по Wi-Fi). Пока работает системное распознавание.")
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                    TratyButton(onClick = vm::downloadModel) { Text("Скачать модель") }
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Hint("Или положите файл gigaam_v3_e2e_ctc_int8.onnx в папку Android/data/ru.my.traty/files/models/gigaam/ на телефоне.")
                }
                is ModelState.Downloading -> {
                    Hint("Скачивание модели… ${(m.progress * 100).roundToInt()}%")
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                    LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth())
                }
                is ModelState.Ready -> {
                    Hint("Модель GigaAM готова. Распознавание работает на телефоне, без интернета.")
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                    TratyOutlinedButton(onClick = vm::deleteModel) { Text("Удалить модель") }
                }
                is ModelState.Failed -> {
                    Hint("Не удалось скачать модель: ${m.message}")
                    Spacer(Modifier.height(TratyTheme.spacing.sm))
                    TratyButton(onClick = vm::downloadModel) { Text("Повторить") }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
}
