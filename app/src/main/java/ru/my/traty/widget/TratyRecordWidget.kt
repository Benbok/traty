package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import ru.my.traty.R
import ru.my.traty.ui.capture.CaptureActivity

/**
 * Минималистичный 1х1 виджет записи голосом:
 * Без какого-либо текста, только крупная кнопка микрофона.
 * Нажатие сразу запускает распознавание речи в CaptureActivity.
 */
class TratyRecordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openCapture = Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            RecordWidgetContent(openCapture)
        }
    }
}

private val recordBackground = ColorProvider(
    day = Color(0xFF059669), // Emerald600
    night = Color(0xFF10B981), // Emerald500
)

@Composable
private fun RecordWidgetContent(openCapture: Intent) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(openCapture)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(recordBackground)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_mic),
                contentDescription = "Сказать трату",
                modifier = GlanceModifier.size(32.dp),
            )
        }
    }
}
