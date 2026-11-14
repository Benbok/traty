package ru.my.traty.data.recurring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.ui.formatRub
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "recurring"
private const val ACTION_PAID = "ru.my.traty.action.RECURRING_PAID"
private const val EXTRA_ID = "recurringId"

/** Есть ли право показывать уведомления (до Android 13 оно выдаётся автоматически). */
fun Context.canNotify(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Раз в сутки (около 9 утра) напоминает о предстоящих списаниях. По каждой дате списания — одно уведомление. */
class RecurringReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.canNotify()) return Result.success()
        val db = (applicationContext as TratyApp).container.db
        val log = applicationContext.getSharedPreferences("recurring_reminders", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val all = db.recurring().getAll()
        val notified = all.associate { it.id to log.getLong("n_${it.id}", -1L) }
        val due = RecurringSchedule.dueReminders(all, today, notified)
        if (due.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)
        for (item in due) {
            val daysLeft = ChronoUnit.DAYS.between(today, item.nextDate())
            val open = PendingIntent.getActivity(
                applicationContext, 0,
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val paid = PendingIntent.getBroadcast(
                applicationContext, item.id.toInt(),
                Intent(applicationContext, RecurringActionReceiver::class.java)
                    .setAction(ACTION_PAID)
                    .putExtra(EXTRA_ID, item.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(RecurringSchedule.reminderTitle(daysLeft))
                .setContentText(
                    if (item.amountVaries) "${item.title}: примерно ${formatRub(item.amountKopecks)}, сумма меняется"
                    else "${item.title}: ${formatRub(item.amountKopecks)}"
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .apply {
                    // С меняющейся суммой кнопка «Списалось» из уведомления создала бы трату с чужой суммой:
                    // точную сумму вводят в приложении
                    if (!item.amountVaries) addAction(0, "Списалось", paid)
                }
                .build()
            try {
                manager.notify(item.id.toInt(), notification)
                log.edit().putLong("n_${item.id}", item.nextEpochDay).apply()
            } catch (_: SecurityException) {
                // Право отозвали между проверкой и показом — в следующий раз
            }
        }
        return Result.success()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Платежи и подписки", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Кнопка «Списалось» в уведомлении: создаёт трату и закрывает уведомление. */
class RecurringActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAID) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        val app = context.applicationContext as TratyApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RecurringRepository(app.container.db).markPaid(id)
                NotificationManagerCompat.from(context).cancel(id.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}

object RecurringReminderWork {
    private const val NAME = "recurring-reminders"

    /** Ставит ежедневную проверку; повторный вызов ничего не меняет. Первый запуск — в ближайшие 9:00. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<RecurringReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
