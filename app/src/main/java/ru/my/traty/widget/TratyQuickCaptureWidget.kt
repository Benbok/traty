package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.my.traty.R
import ru.my.traty.ui.designsystem.tokens.DarkTratyColors
import ru.my.traty.ui.designsystem.tokens.LightTratyColors

/** Два независимых быстрых действия в одном виджете 2×1. */
class TratyQuickCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val voice = quickVoiceIntent(context)
        val receipt = quickReceiptIntent(context)
        provideContent {
            Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                QuickCaptureButton(
                    R.drawable.ic_widget_mic, "Сказать", "Сказать трату", voice,
                    GlanceModifier.defaultWeight().fillMaxHeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                QuickCaptureButton(
                    R.drawable.ic_widget_camera, "Чек", "Сфотографировать чек", receipt,
                    GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

class TratyQuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TratyQuickCaptureWidget()
}

@Composable
private fun QuickCaptureButton(
    icon: Int,
    label: String,
    description: String,
    intent: Intent,
    modifier: GlanceModifier,
) {
    val background = ColorProvider(LightTratyColors.primaryContainer, DarkTratyColors.primaryContainer)
    val foreground = ColorProvider(LightTratyColors.onPrimaryContainer, DarkTratyColors.onPrimaryContainer)
    Column(
        modifier.cornerRadius(20.dp).background(background)
            .clickable(actionStartActivity(intent), rippleOverride = R.drawable.traty_widget_press_shortcut)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = androidx.glance.ColorFilter.tint(foreground),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(label, style = TextStyle(color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
