package ru.my.traty.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class VoiceEngineSettingTest {
    private val settings get() = ApplicationProvider.getApplicationContext<TratyApp>().container.settings

    @Test
    fun gigaAmIsTheDefaultEngine() {
        assertEquals(VoiceEngine.GIGAAM, AppSettings().voiceEngine)
    }

    @Test
    fun choiceIsStoredAndSaveDoesNotResetIt() = runTest {
        // Хранилище настроек общее для тестов в одном процессе — возвращаем как было
        try {
            settings.setVoiceEngine(VoiceEngine.SYSTEM)
            assertEquals(VoiceEngine.SYSTEM, settings.current().voiceEngine)
            // Сохранение остальных настроек (экран ключей ИИ) не должно сбрасывать выбор движка
            settings.save(settings.current().copy(periodStartDay = 5))
            assertEquals(VoiceEngine.SYSTEM, settings.current().voiceEngine)
            settings.setVoiceEngine(VoiceEngine.GIGAAM)
            assertEquals(VoiceEngine.GIGAAM, settings.current().voiceEngine)
        } finally {
            settings.setVoiceEngine(VoiceEngine.GIGAAM)
            settings.setPeriodStartDay(1)
        }
    }
}
