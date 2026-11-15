package ru.my.traty.data.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.recurring.canNotify
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val CHANNEL_ID = "budget_alerts"
private const val PREFS = "budget_alerts"
private const val SENT = "sent"

/**
 * Следит за тратами текущего расчётного периода и показывает уведомления о плане (80 % и 100 %).
 * Работает, пока жив процесс приложения: траты добавляются только из него (экран, панель записи,
 * фоновый разбор фраз), поэтому отдельный фоновый воркер не нужен.
 */
class BudgetAlertWatcher(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) = scope.launch {
        settings.flow.map { it.budgetAlertsEnabled to it.periodStartDay }.distinctUntilChanged()
            .flatMapLatest { (enabled, startDay) ->
                if (!enabled) emptyFlow<BillingPeriod>() else days().map { BillingPeriod.containing(it, startDay) }.distinctUntilChanged()
            }
            .flatMapLatest { period -> targets(period) }
            // Пачка записей (несколько трат одной фразой, «Отменить» в снекбаре) не даёт лишних уведомлений
            .debounce(3_000)
            .collect { (month, targets) ->
                try {
                    notify(month, targets)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Уведомление не должно ронять приложение; попробуем при следующей трате
                }
            }
    }

    private fun targets(period: BillingPeriod): Flow<Pair<Int, List<AlertTarget>>> = combine(
        db.expenses().observeRange(period.startMillis(zone), period.endMillisExclusive(zone)),
        db.budgets().observeAll(),
        db.monthBudgets().observeAll(),
        db.categories().observeActiveWithSubs(),
    ) { expenses, budgets, totals, cats ->
        val month = PlanMonth.of(period)
        val byId = cats.associateBy { it.category.id }
        val spends = expenses.map {
            PlanSpend(
                it.expense.categoryId, it.expense.subcategoryId, it.expense.amountKopecks,
                Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate(),
            )
        }
        month to BudgetAlerts.targets(budgets, totals, month, spends) nameOf@{ key ->
            val cat = byId[key.categoryId] ?: return@nameOf null
            val sub = key.subcategoryId?.let { id -> cat.subs.firstOrNull { it.id == id }?.name }
            if (key.subcategoryId != null && sub == null) null
            else listOfNotNull(cat.category.name, sub).joinToString(" · ")
        }
    }

    /** Сегодняшняя дата; новое значение — сразу после полуночи, чтобы период сменился вовремя. */
    private fun days(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(zone)
            emit(now.toLocalDate())
            val next = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(5)
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000))
        }
    }

    private fun notify(month: Int, targets: List<AlertTarget>) {
        if (!context.canNotify()) return
        val sent = BudgetAlerts.keepForMonth(prefs.getStringSet(SENT, emptySet()).orEmpty(), month)
        val due = BudgetAlerts.due(month, targets, sent)
        if (due.alerts.isEmpty() && due.alsoMark.isEmpty()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shown = HashSet<String>()
        for (alert in due.alerts) {
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(alert.title)
                .setContentText(alert.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                // Одно уведомление на план: 100 % заменяет прежнее 80 %
                NotificationManagerCompat.from(context).notify(alert.targetId, NOTIFICATION_ID, n)
                shown += alert.key
            } catch (_: SecurityException) {
                return
            }
        }
        prefs.edit().putStringSet(SENT, sent + shown + due.alsoMark).apply()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "План трат", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_ID = 7101
    }
}
