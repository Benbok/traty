package ru.my.traty.data.debt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.recurring.canNotify
import ru.my.traty.ui.formatRub
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "recurring"
private const val ACTION_PAID = "ru.my.traty.action.DEBT_PAID"
private const val EXTRA_ID = "debtId"

/** Номера уведомлений о покупках не пересекаются с уведомлениями о подписках (у них id платежа). */
private const val NOTIFICATION_BASE = 1_000_000

private fun notificationId(debtId: Long): Int = NOTIFICATION_BASE + debtId.toInt()

/** Раз в сутки (около 9 утра) напоминает о платежах по рассрочкам и кредитам: за день, в день платежа и после срока. */
class DebtReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.canNotify()) return Result.success()
        val repo = (applicationContext as TratyApp).container.debts
        val log = applicationContext.getSharedPreferences("debt_reminders", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val items = repo.activeWithPayments()
        val notified = items.associate { (debt, _) -> debt.id to log.getLong("n_${debt.id}", -1L) }
        val due = DebtReminders.due(items, today, notified)
        if (due.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)
        for (reminder in due) {
            try {
                manager.notify(notificationId(reminder.debtId), notificationOf(reminder))
                log.edit().putLong("n_${reminder.debtId}", today.toEpochDay()).apply()
            } catch (_: SecurityException) {
                // Право отозвали между проверкой и показом — в следующий раз
            }
        }
        return Result.success()
    }

    private fun notificationOf(reminder: DebtReminder) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(DebtReminders.title(reminder.daysLeft))
        .setContentText("«${reminder.title}»: ${formatRub(reminder.amountKopecks)}, платёж ${reminder.number} из ${reminder.count}")
        .setContentIntent(
            PendingIntent.getActivity(
                applicationContext, 0,
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setAutoCancel(true)
        .addAction(
            0, "Оплатил",
            PendingIntent.getBroadcast(
                applicationContext, notificationId(reminder.debtId),
                Intent(applicationContext, DebtActionReceiver::class.java)
                    .setAction(ACTION_PAID)
                    .putExtra(EXTRA_ID, reminder.debtId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Платежи и подписки", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Кнопка «Оплатил» в уведомлении: вносит ближайший платёж по графику траты и закрывает уведомление. */
class DebtActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAID) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        val repo = (context.applicationContext as TratyApp).container.debts
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val next = repo.status(id)?.next
                if (next != null && repo.addPayment(id, next.totalKopecks)) {
                    NotificationManagerCompat.from(context).cancel(notificationId(id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Уведомление остаётся: платёж можно внести на экране покупки
            } finally {
                pending.finish()
            }
        }
    }
}

object DebtReminderWork {
    private const val NAME = "debt-reminders"

    /** Ставит ежедневную проверку; повторный вызов ничего не меняет. Первый запуск — в ближайшие 9:00. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
