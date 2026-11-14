package ru.my.traty.widget

import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.ZoneId

/** Расходы для виджета: за сегодня и за текущий расчётный период (включая записи сверки). Пополнения не входят. */
data class WidgetTotals(val todayKopecks: Long, val periodKopecks: Long)

/** Данные для расширенного виджета: общие суммы и последние траты за сегодня. */
data class WidgetData(
    val totals: WidgetTotals,
    val todayExpenses: List<ExpenseWithCategory> = emptyList(),
)

suspend fun loadWidgetTotals(db: AppDatabase, settings: AppSettings, today: LocalDate, zone: ZoneId): WidgetTotals {
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val period = BillingPeriod.containing(today, settings.periodStartDay)
    return WidgetTotals(
        todayKopecks = db.expenses().sumRange(dayStart, dayEnd),
        periodKopecks = db.expenses().sumRange(period.startMillis(zone), period.endMillisExclusive(zone)),
    )
}

suspend fun loadWidgetData(
    db: AppDatabase,
    settings: AppSettings,
    today: LocalDate,
    zone: ZoneId,
    limit: Int = 5,
): WidgetData {
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val totals = loadWidgetTotals(db, settings, today, zone)
    val expenses = db.expenses().getRange(dayStart, dayEnd, limit)
    return WidgetData(totals = totals, todayExpenses = expenses)
}
