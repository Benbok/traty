package ru.my.traty.data.budget

import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

/** Выбор и правка общего лимита на месяц. Правила повтора те же, что у планов категорий ([BudgetPlans]). */
object TotalBudgets {
    /** Лимит месяца [month]: строка этого месяца или последняя повторяющаяся до него; null — лимита нет. */
    fun effective(rows: List<MonthBudget>, month: Int): MonthBudget? {
        val row = rows.filter { it.month == month }.maxByOrNull { it.id }
            ?: rows.filter { it.month < month && it.repeats }.maxWithOrNull(compareBy({ it.month }, { it.id }))
        return row?.takeIf { it.amountKopecks > 0 }
    }

    /** Строка, которую нужно записать, чтобы задать лимит [amountKopecks] на [month]. */
    fun set(rows: List<MonthBudget>, month: Int, amountKopecks: Long, repeats: Boolean): MonthBudget {
        val existing = rows.filter { it.month == month }.maxByOrNull { it.id }
        return (existing ?: MonthBudget(month = month, amountKopecks = 0)).copy(amountKopecks = amountKopecks, repeats = repeats)
    }

    data class Removal(val delete: List<MonthBudget>, val insert: MonthBudget?)

    /** Убрать лимит начиная с [month]. Если от прошлых месяцев тянется повтор, ставится строка «без лимита». */
    fun removeFrom(rows: List<MonthBudget>, month: Int): Removal {
        val delete = rows.filter { it.month >= month }
        val inherited = effective(rows - delete.toSet(), month) != null
        return Removal(delete, if (inherited) MonthBudget(month = month, amountKopecks = 0, repeats = true) else null)
    }
}

/** Как идёт общий лимит в периоде: [spent] — все траты периода, по всем категориям. */
data class TotalProgress(
    val limit: MonthBudget,
    val spent: Long,
    val status: PlanStatus,
    val perDay: Long?,
    val forecast: Long?,
) {
    val planned: Long get() = limit.amountKopecks
    val remaining: Long get() = planned - spent
    val fraction: Float get() = if (planned > 0) spent.toFloat() / planned else 0f

    companion object {
        fun of(limit: MonthBudget, spent: Long, period: BillingPeriod, today: LocalDate): TotalProgress {
            val pace = PlanProgresses.pace(limit.amountKopecks, spent, period, today)
            return TotalProgress(limit, spent, pace.status, pace.perDay, pace.forecast)
        }
    }
}
