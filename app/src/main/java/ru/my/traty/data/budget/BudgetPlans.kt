package ru.my.traty.data.budget

import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.period.SpendingPaces
import java.time.LocalDate
import java.time.YearMonth

/** Категория или её подкатегория, к которой относится план. */
data class PlanKey(val categoryId: Long, val subcategoryId: Long? = null)

val Budget.key: PlanKey get() = PlanKey(categoryId, subcategoryId)

/** Месяц плана в виде ГГГГММ. */
object PlanMonth {
    fun of(ym: YearMonth): Int = ym.year * 100 + ym.monthValue

    /** Месяц, в котором начинается расчётный период. */
    fun of(period: BillingPeriod): Int = of(YearMonth.from(period.startDate))

    fun toYearMonth(month: Int): YearMonth = YearMonth.of(month / 100, month % 100)

    /** Расчётный период, который начинается в месяце [month]. */
    fun period(month: Int, startDay: Int): BillingPeriod =
        BillingPeriod.containing(toYearMonth(month).atDay(startDay.coerceIn(1, 28)), startDay)
}

/** Трата, сведённая к тому, что нужно планам. */
data class PlanSpend(val categoryId: Long, val subcategoryId: Long?, val amountKopecks: Long, val date: LocalDate)

object BudgetPlans {
    /**
     * Планы, действующие в месяце [month]: для каждой категории (подкатегории) берётся строка этого месяца,
     * а если её нет — последняя повторяющаяся строка до него. Строки «без плана» (сумма 0) в ответ не попадают.
     */
    fun effective(rows: List<Budget>, month: Int): List<Budget> =
        rows.groupBy { it.key }.values.mapNotNull { forKey ->
            forKey.filter { it.month == month }.maxByOrNull { it.id }
                ?: forKey.filter { it.month < month && it.repeats }.maxWithOrNull(compareBy({ it.month }, { it.id }))
        }.filter { it.amountKopecks > 0 }

    /** Что сделать с базой, чтобы задать план [amountKopecks] для [key] на [month]. */
    fun set(rows: List<Budget>, key: PlanKey, month: Int, amountKopecks: Long, repeats: Boolean): Budget {
        val existing = rows.filter { it.key == key && it.month == month }.maxByOrNull { it.id }
        return (existing ?: Budget(categoryId = key.categoryId, subcategoryId = key.subcategoryId, month = month, amountKopecks = 0))
            .copy(amountKopecks = amountKopecks, repeats = repeats)
    }

    /** Изменения для «убрать план с [month]»: какие строки удалить и какую вставить. */
    data class Removal(val delete: List<Budget>, val insert: Budget?)

    /**
     * Убрать план [key] начиная с [month]: удаляются строки этого и следующих месяцев. Если от прошлых месяцев
     * остаётся повторяющийся план, ставится строка «без плана», чтобы он не вернулся.
     */
    fun removeFrom(rows: List<Budget>, key: PlanKey, month: Int): Removal {
        val delete = rows.filter { it.key == key && it.month >= month }
        val rest = rows - delete.toSet()
        val inherited = effective(rest.filter { it.key == key }, month).isNotEmpty()
        val stop = if (inherited) {
            Budget(categoryId = key.categoryId, subcategoryId = key.subcategoryId, month = month, amountKopecks = 0, repeats = true)
        } else null
        return Removal(delete, stop)
    }

    /** Потрачено по каждому ключу: план категории учитывает все её подкатегории. */
    fun spent(items: List<PlanSpend>): Map<PlanKey, Long> {
        val out = HashMap<PlanKey, Long>()
        for (s in items) {
            out.merge(PlanKey(s.categoryId), s.amountKopecks, Long::plus)
            if (s.subcategoryId != null) out.merge(PlanKey(s.categoryId, s.subcategoryId), s.amountKopecks, Long::plus)
        }
        return out
    }

    /** Сколько периодов назад смотреть для подсказки суммы плана. */
    const val HINT_PERIODS = 3

    /**
     * Подсказка суммы плана: средняя трата по [key] за [HINT_PERIODS] прошлых периода минус 10 %,
     * округлённая до 100 ₽ (но не меньше 100 ₽). Периоды раньше первой траты в истории не считаются,
     * чтобы новая история не занижала среднюю. null — трат по ключу не было.
     */
    fun hint(items: List<PlanSpend>, key: PlanKey, period: BillingPeriod, startDay: Int): Long? {
        val first = items.minOfOrNull { it.date } ?: return null
        val periods = generateSequence(period.previous(startDay)) { it.previous(startDay) }
            .take(HINT_PERIODS)
            .filter { !it.endDate.isBefore(first) }
            .toList()
        if (periods.isEmpty()) return null
        val total = items.filter { s ->
            s.categoryId == key.categoryId && (key.subcategoryId == null || s.subcategoryId == key.subcategoryId) &&
                periods.any { s.date in it }
        }.sumOf { it.amountKopecks }
        if (total <= 0) return null
        val target = total / periods.size * 9 / 10
        return ((target + 5_000) / 10_000 * 10_000).coerceAtLeast(10_000)
    }
}

enum class PlanStatus {
    /** Всё в порядке. */
    OK,

    /** Потрачено 80 % плана или больше. */
    NEAR,

    /** Пока в плане, но по текущему темпу к концу периода будет перерасход. */
    AT_RISK,

    /** План превышен. */
    OVER,
}

/** Как идёт план в периоде. */
data class PlanProgress(
    val plan: Budget,
    val spent: Long,
    val status: PlanStatus,
    /** Можно тратить в день до конца периода, включая сегодня; null — период прошёл или остатка нет. */
    val perDay: Long?,
    /** Прогноз на конец периода по текущему темпу; null — период прошёл или прогноз ещё ненадёжен. */
    val forecast: Long?,
) {
    val planned: Long get() = plan.amountKopecks
    val remaining: Long get() = planned - spent
    val fraction: Float get() = if (planned > 0) spent.toFloat() / planned else 0f
}

object PlanProgresses {
    /** Прогноз по темпу начинаем показывать с этого дня периода: в первые дни он скачет (аренда в 1-й день и т. п.). */
    const val MIN_DAYS_FOR_FORECAST = 7

    /** Порог «почти исчерпан», в процентах. */
    const val NEAR_PERCENT = 80

    fun of(plan: Budget, spent: Long, period: BillingPeriod, today: LocalDate): PlanProgress {
        val pace = SpendingPaces.of(period, today, spent)
        val running = pace != null && pace.forecast != null
        val forecast = pace?.forecast?.takeIf { pace.daysPassed >= MIN_DAYS_FOR_FORECAST }
        val remaining = plan.amountKopecks - spent
        val daysWithToday = if (pace != null && running) pace.daysLeft + 1 else 0
        val perDay = if (running && remaining > 0 && daysWithToday > 0) remaining / daysWithToday else null
        val status = when {
            spent > plan.amountKopecks -> PlanStatus.OVER
            forecast != null && forecast > plan.amountKopecks && spent < plan.amountKopecks -> PlanStatus.AT_RISK
            spent * 100 >= plan.amountKopecks * NEAR_PERCENT -> PlanStatus.NEAR
            else -> PlanStatus.OK
        }
        return PlanProgress(plan, spent, status, perDay, forecast)
    }

    /** Прогресс по всем [plans] периода; сначала самые тревожные, потом по доле потраченного. */
    fun all(plans: List<Budget>, spent: Map<PlanKey, Long>, period: BillingPeriod, today: LocalDate): List<PlanProgress> =
        plans.map { of(it, spent[it.key] ?: 0L, period, today) }
            .sortedWith(compareByDescending<PlanProgress> { it.status.ordinal }.thenByDescending { it.fraction })
}

/** Сводка по всем планам периода: для шапки карточки «План трат». */
data class PlanOverview(
    val planned: Long,
    val spent: Long,
    /** Доля прошедших дней периода (0..1), считая сегодняшний; null — период ещё не начался. */
    val elapsed: Float?,
    /** Сколько дней осталось, считая сегодняшний; 0 — период прошёл. */
    val daysLeft: Int,
    val daysTotal: Int,
) {
    val remaining: Long get() = planned - spent
    val fraction: Float get() = if (planned > 0) spent.toFloat() / planned else 0f

    /** Можно тратить в день до конца периода; null — период не идёт или остатка нет. */
    val perDay: Long? get() = if (daysLeft > 0 && elapsed != null && remaining > 0) remaining / daysLeft else null

    companion object {
        fun of(planned: Long, spent: Long, period: BillingPeriod, today: LocalDate): PlanOverview {
            val total = (java.time.temporal.ChronoUnit.DAYS.between(period.startDate, period.endDate) + 1).toInt()
            val pace = SpendingPaces.of(period, today, spent)
            val running = pace?.forecast != null
            val elapsed = pace?.let { it.daysPassed.toFloat() / total }
            val left = if (running) pace!!.daysLeft + 1 else 0
            return PlanOverview(planned, spent, elapsed, left, total)
        }
    }
}
