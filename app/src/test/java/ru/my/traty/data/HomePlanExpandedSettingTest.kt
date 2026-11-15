package ru.my.traty.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class HomePlanExpandedSettingTest {
    private val settings get() = ApplicationProvider.getApplicationContext<TratyApp>().container.settings

    @Test
    fun planIsCollapsedByDefault() {
        assertFalse(AppSettings().homePlanExpanded)
    }

    @Test
    fun choiceIsStoredAndSaveDoesNotResetIt() = runTest {
        try {
            settings.setHomePlanExpanded(true)
            assertTrue(settings.current().homePlanExpanded)
            // Сохранение экрана настроек не должно сбрасывать выбор
            settings.save(settings.current().copy(periodStartDay = 5))
            assertTrue(settings.current().homePlanExpanded)
            settings.setHomePlanExpanded(false)
            assertFalse(settings.current().homePlanExpanded)
        } finally {
            settings.setHomePlanExpanded(false)
            settings.setPeriodStartDay(1)
        }
    }
}
