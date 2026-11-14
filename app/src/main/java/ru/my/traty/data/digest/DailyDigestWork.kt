package ru.my.traty.data.digest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.my.traty.TratyApp
import ru.my.traty.data.recurring.canNotify
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "daily_digest"
private const val NOTIFICATION_ID = 7001

/** Раз в сутки около 20:00 показывает «Итог дня»: сколько потрачено, как это соотносится с обычным и что поступило. */
class DailyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TratyApp
        if (!app.container.settings.current().dailyDigestEnabled) return Result.success()
        if (!applicationContext.canNotify()) return Result.success()

        val log = applicationContext.getSharedPreferences("daily_digest", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        // Один итог в сутки, даже если воркер запустится дважды
        if (log.getLong("last", -1L) == today.toEpochDay()) return Result.success()

        val db = app.container.db
        val digest = DailyDigests.build(
            expenses = db.expenses().getAllWithCategory(),
            incomes = db.incomes().getAll(),
            today = today,
            zone = ZoneId.systemDefault(),
        ) ?: return Result.success()

        ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(digest.title)
            .setContentText(digest.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(digest.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            log.edit().putLong("last", today.toEpochDay()).apply()
        } catch (_: SecurityException) {
            // Право отозвали между проверкой и показом — завтра
        }
        return Result.success()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Итог дня", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

object DailyDigestWork {
    private const val NAME = "daily-digest"
    private val AT: LocalTime = LocalTime.of(20, 0)

    /** Ставит ежедневный итог; повторный вызов ничего не меняет. Первый запуск — в ближайшие 20:00. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(AT)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
