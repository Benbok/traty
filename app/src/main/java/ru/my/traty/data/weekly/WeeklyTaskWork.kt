package ru.my.traty.data.weekly

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
import androidx.work.workDataOf
import ru.my.traty.TratyApp
import ru.my.traty.data.recurring.canNotify
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "weekly_tasks"
private const val NOTIFICATION_ID = 7201
private const val KIND = "kind"
private const val KIND_NEW = "new"
private const val KIND_RESULT = "result"

/**
 * Раз в сутки: в понедельник утром создаёт задания недели и сообщает о них,
 * в воскресенье вечером присылает итог недели. В остальные дни ничего не делает.
 */
class WeeklyTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TratyApp
        val today = LocalDate.now()
        val kind = inputData.getString(KIND)
        val repo = app.container.weeklyTasks
        val due = (kind == KIND_NEW && today.dayOfWeek == DayOfWeek.MONDAY) ||
            (kind == KIND_RESULT && today.dayOfWeek == DayOfWeek.SUNDAY)
        if (!due) return Result.success()
        // Задания создаются в понедельник, даже если уведомления выключены
        val week = repo.ensureWeek(today)
        if (!app.container.settings.current().weeklyTasksNotify || !applicationContext.canNotify()) return Result.success()
        if (week.isEmpty()) return Result.success()

        val log = applicationContext.getSharedPreferences("weekly_tasks", Context.MODE_PRIVATE)
        val logKey = "$kind-${Weeks.monday(today).toEpochDay()}"
        if (log.getBoolean(logKey, false)) return Result.success()

        val (title, body) = if (kind == KIND_NEW) {
            "Задания на неделю" to week.joinToString("\n") { "• ${it.title}" }
        } else {
            val spends = app.container.db.expenses().getAllWithCategory().toTaskSpends(ZoneId.systemDefault())
            // Вечер воскресенья: считаем неделю законченной, итог предварительный до полуночи
            WeeklyTaskTexts.weekResult(week.map { WeeklyProgress.of(it, spends, today.plusDays(1)) })
        }
        show(title, body)
        log.edit().putBoolean(logKey, true).apply()
        return Result.success()
    }

    private fun show(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Задания на неделю", NotificationManager.IMPORTANCE_DEFAULT)
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Право отозвали между проверкой и показом
        }
    }
}

object WeeklyTaskWork {
    /** Ставит две ежедневные проверки: утро понедельника и вечер воскресенья. Повторный вызов ничего не меняет. */
    fun schedule(context: Context) {
        enqueue(context, "weekly-tasks-new", KIND_NEW, LocalTime.of(9, 0))
        enqueue(context, "weekly-tasks-result", KIND_RESULT, LocalTime.of(20, 30))
    }

    private fun enqueue(context: Context, name: String, kind: String, at: LocalTime) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(at)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<WeeklyTaskWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf(KIND to kind))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
