package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.MainActivity
import ru.my.traty.TestTratyApp
import ru.my.traty.ui.capture.CaptureActivity
import ru.my.traty.ui.receipt.RECEIPT_CAMERA_ACTION

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class QuickCaptureIntentsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun voiceOpensImmediateRecordingPanel() {
        val intent = quickVoiceIntent(context)
        assertEquals(CaptureActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun cameraOpensReceiptActionInMainWithoutDuplicatingItsActivity() {
        val intent = quickReceiptIntent(context)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(RECEIPT_CAMERA_ACTION, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun buttonsHaveDistinctPendingIntentIdentities() {
        assertFalse(quickVoiceIntent(context).filterEquals(quickReceiptIntent(context)))
        assertFalse(Intent(context, MainActivity::class.java).filterEquals(quickReceiptIntent(context)))
    }
}
