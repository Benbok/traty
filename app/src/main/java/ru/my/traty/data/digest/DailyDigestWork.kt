package ru.my.traty.data.digest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.my.traty.ACTION_OPEN_PURCHASE_GOALS
import ru.my.traty.MainActivity
import ru.my.traty.TratyApp
import ru.my.traty.data.recurring.canNotify
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "daily_digest"
private const val NOTIFICATION_ID = 7001
private const val SLOT_DATE = "date"
private const val SLOT_ZONE = "zone"
private const val SLOT_MINUTE = "minute"
private const val SENT_DATES = "sent_dates"
private const val LEGACY_LAST = "last"

class DailyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val container = (context as TratyApp).container
        val settings = container.settings.digestSettings.first()
        if (!settings.enabled) return Result.success()

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val slot = readSlot()
        val log = context.getSharedPreferences(CHANNEL_ID, Context.MODE_PRIVATE)
        val today = now.atZone(zone).toLocalDate()
        val sentToday = today.takeIf { log.wasSent(it) }

        // Если часы перевели назад, эта же задача должна дождаться своего времени.
        if (slot != null && slot.zone == zone && slot.time == settings.time && slot.date == today &&
            now < slot.at && sentToday == null
        ) return Result.retry()

        DailyDigestWork.schedule(context, settings)
        if (slot == null || !slot.canSend(now, zone, settings.time, sentToday) || !context.canNotify()) {
            return Result.success()
        }

        val summary = DailyDigests.build(
            expenses = container.db.expenses().getAllWithCategory(),
            incomes = container.db.incomes().getAll(),
            today = today,
            zone = zone,
        )
        val goals = container.purchaseGoals.listActive().map {
            DigestGoalReminder(it.goal.title, it.remainingToday(today, zone))
        }
        val message = digestNotification(summary, goals) ?: return Result.success()

        val current = container.settings.digestSettings.first()
        synchronized(log) {
            if (current.enabled && slot.canSend(
                    Instant.now(), ZoneId.systemDefault(), current.time, today.takeIf { log.wasSent(it) },
                )
            ) publish(context, log, slot.date, message)
        }
        return Result.success()
    }

    private fun readSlot(): DigestSlot? {
        val date = inputData.getLong(SLOT_DATE, Long.MIN_VALUE)
        val zone = inputData.getString(SLOT_ZONE) ?: return null
        val minute = inputData.getInt(SLOT_MINUTE, -1)
        if (date == Long.MIN_VALUE || minute !in 0 until 24 * 60) return null
        return DigestSlot(LocalDate.ofEpochDay(date), ZoneId.of(zone), LocalTime.of(minute / 60, minute % 60))
    }
}

private fun publish(context: Context, log: SharedPreferences, date: LocalDate, message: DigestNotification) {
    ensureDigestChannel(context)
    val launch = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (message.opensPurchaseGoals) launch.action = ACTION_OPEN_PURCHASE_GOALS
    val open = PendingIntent.getActivity(
        context, NOTIFICATION_ID, launch,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(message.title)
        .setContentText(message.body.lineSequence().first())
        .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        log.edit().putStringSet(SENT_DATES, log.getStringSet(SENT_DATES, emptySet()).orEmpty() + date.toString()).apply()
    } catch (_: SecurityException) {
        // Разрешение на уведомления отозвали после проверки.
    }
}

private fun ensureDigestChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(CHANNEL_ID, "Итог дня", NotificationManager.IMPORTANCE_DEFAULT)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private fun SharedPreferences.wasSent(date: LocalDate): Boolean =
    getLong(LEGACY_LAST, Long.MIN_VALUE) == date.toEpochDay() ||
        date.toString() in getStringSet(SENT_DATES, emptySet()).orEmpty()

internal object DailyDigestWork {
    private const val LEGACY_NAME = "daily-digest"

    fun schedule(context: Context, settings: DigestSettings, clockChanged: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_NAME)
        if (!settings.enabled) return

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val log = context.getSharedPreferences(CHANNEL_ID, Context.MODE_PRIVATE)
        val slot = nextDigestSlot(now, zone, settings.time, today.takeIf { log.wasSent(it) })
        val minute = slot.time.toSecondOfDay() / 60
        val request = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(Duration.between(now, slot.at).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SLOT_DATE to slot.date.toEpochDay(), SLOT_ZONE to zone.id, SLOT_MINUTE to minute))
            .build()
        // Старые слоты после изменения времени или зоны проверят настройки перед отправкой.
        val policy = if (clockChanged) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        wm.enqueueUniqueWork("daily-digest-${slot.date}-${zone.id}-$minute", policy, request)
    }
}

/** Перестраивает расписание после смены часового пояса или ручной настройки часов. */
class DailyDigestClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val app = context.applicationContext as TratyApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyDigestWork.schedule(app, app.container.settings.digestSettings.first(), clockChanged = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // При следующем запуске расписание заново прочитается из настроек.
            } finally {
                pending.finish()
            }
        }
    }
}
