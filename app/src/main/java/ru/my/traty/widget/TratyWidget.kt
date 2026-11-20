package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.my.traty.MainActivity
import ru.my.traty.R
import ru.my.traty.TratyApp
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.ui.capture.CaptureActivity
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneId

/**
 * Виджет рабочего стола со сводкой и списком трат:
 * - Суммы расходов за сегодня и за расчётный период
 * - Список последних трат за сегодня с категориями
 * - Кнопка быстрой записи «Сказать»
 */
class TratyWidget : GlanceAppWidget() {
    companion object {
        private val COMPACT_SIZE = DpSize(120.dp, 80.dp)
        private val MEDIUM_SIZE = DpSize(200.dp, 120.dp)
        private val LARGE_SIZE = DpSize(260.dp, 180.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, MEDIUM_SIZE, LARGE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TratyApp).container
        val data = loadWidgetData(
            db = container.db,
            settings = container.settings.current(),
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
            limit = 5,
        )
        val openCapture = Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openMain = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            WidgetContent(data, openCapture, openMain)
        }
    }
}

private val cardBackground = ColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1E293B), // Slate800
)
private val emeraldPrimary = ColorProvider(
    day = Color(0xFF059669), // Emerald600
    night = Color(0xFF10B981), // Emerald500
)
private val textPrimary = ColorProvider(
    day = Color(0xFF0F172A), // Slate900
    night = Color(0xFFF8FAFC), // Slate50
)
private val textSecondary = ColorProvider(
    day = Color(0xFF64748B), // Slate500
    night = Color(0xFF94A3B8), // Slate400
)
private val dividerColor = ColorProvider(
    day = Color(0xFFE2E8F0), // Slate200
    night = Color(0xFF334155), // Slate700
)

@Composable
private fun WidgetContent(data: WidgetData, openCapture: Intent, openMain: Intent) {
    val size = LocalSize.current
    val isSmall = size.height < 110.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(cardBackground)
            .padding(12.dp),
    ) {
        HeaderRow(
            totals = data.totals,
            openCapture = openCapture,
            openMain = openMain,
            compact = isSmall,
        )

        if (!isSmall) {
            Spacer(GlanceModifier.height(8.dp))
            Divider()
            Spacer(GlanceModifier.height(8.dp))

            ExpensesList(
                expenses = data.todayExpenses,
                openMain = openMain,
                maxItems = if (size.height >= 170.dp) 4 else 2,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    totals: WidgetTotals,
    openCapture: Intent,
    openMain: Intent,
    compact: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(openMain), rippleOverride = R.drawable.traty_widget_press_row),
        ) {
            Text(
                text = "СЕГОДНЯ",
                style = TextStyle(
                    color = textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = formatRub(totals.todayKopecks),
                style = TextStyle(
                    color = textPrimary,
                    fontSize = if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (!compact) {
                Text(
                    text = "Период: ${formatRub(totals.periodKopecks)}",
                    style = TextStyle(
                        color = textSecondary,
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        Spacer(GlanceModifier.width(8.dp))

        Box(
            modifier = GlanceModifier
                .cornerRadius(16.dp)
                .background(emeraldPrimary)
                .clickable(actionStartActivity(openCapture), rippleOverride = R.drawable.traty_widget_press_action)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_mic),
                    contentDescription = "Сказать трату",
                    modifier = GlanceModifier.size(16.dp),
                )
                if (!compact) {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "Сказать",
                        style = TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dividerColor),
    ) {}
}

@Composable
private fun ExpensesList(
    expenses: List<ExpenseWithCategory>,
    openMain: Intent,
    maxItems: Int,
) {
    if (expenses.isEmpty()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openMain), rippleOverride = R.drawable.traty_widget_press_row)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Пока нет трат за сегодня",
                style = TextStyle(
                    color = textSecondary,
                    fontSize = 12.sp,
                ),
            )
        }
    } else {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openMain), rippleOverride = R.drawable.traty_widget_press_row),
        ) {
            expenses.take(maxItems).forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(GlanceModifier.height(6.dp))
                }
                ExpenseItemRow(item)
            }
        }
    }
}

@Composable
private fun ExpenseItemRow(item: ExpenseWithCategory) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.categoryEmoji,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(GlanceModifier.width(6.dp))
        val title = item.expense.place?.takeIf { it.isNotBlank() } ?: item.categoryName
        val subtitle = if (item.expense.place != null && item.expense.place.isNotBlank()) item.categoryName else item.subcategoryName
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    color = textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = textSecondary,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = formatRub(item.expense.amountKopecks),
            style = TextStyle(
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
