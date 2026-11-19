package ru.my.traty.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class DailyDigestSettingTest {
    private val settings get() = ApplicationProvider.getApplicationContext<TratyApp>().container.settings

    @Test
    fun savedLocalTimeIsUsedForScheduleAndRestoredFromCopy() = runTest {
        val previous = settings.current().portable()
        val selected = LocalTime.of(21, 45)
        try {
            settings.setDailyDigestTime(selected)
            assertEquals(selected, settings.digestSettings.first().time)
            val copy = settings.current().portable()
            settings.setDailyDigestTime(LocalTime.of(18, 30))
            settings.restore(copy)
            assertEquals(selected, settings.current().dailyDigestTime)
            assertEquals(selected, settings.digestSettings.first().time)
        } finally {
            settings.restore(previous)
        }
    }

    @Test
    fun oldCopyOrInvalidValuePreservesCurrentChoice() = runTest {
        val previous = settings.current().portable()
        val selected = LocalTime.of(7, 5)
        try {
            settings.setDailyDigestTime(selected)
            settings.restore(PortableSettings())
            assertEquals(selected, settings.current().dailyDigestTime)
            settings.restore(PortableSettings(dailyDigestMinutes = 1440))
            assertEquals(selected, settings.current().dailyDigestTime)
        } finally {
            settings.restore(previous)
        }
    }
}
