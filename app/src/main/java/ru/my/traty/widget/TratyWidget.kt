package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.my.traty.TratyApp
import ru.my.traty.ui.capture.CaptureActivity
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneId

/** Виджет рабочего стола: кнопка «Сказать трату» и расходы за сегодня и за расчётный период. */
class TratyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TratyApp).container
        val totals = loadWidgetTotals(container.db, container.settings.current(), LocalDate.now(), ZoneId.systemDefault())
        val openCapture = Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent { WidgetContent(totals, openCapture) }
    }
}

private val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1B1F))
private val primaryText = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
private val secondaryText = ColorProvider(day = Color(0xFF605D66), night = Color(0xFFC9C5CA))

@androidx.compose.runtime.Composable
private fun WidgetContent(totals: WidgetTotals, openCapture: Intent) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(background).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            text = "🎤 Сказать трату",
            onClick = actionStartActivity(openCapture),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(GlanceModifier.height(8.dp))
        TotalRow("Сегодня", formatRub(totals.todayKopecks))
        TotalRow("За период", formatRub(totals.periodKopecks))
    }
}

@androidx.compose.runtime.Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(color = secondaryText, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
        Text(value, style = TextStyle(color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}
