package ru.my.traty.data.recurring

import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import ru.my.traty.data.isPurchaseGoalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Траты, похожие на подписку: одно место, почти одна сумма, ровный интервал. */
data class RecurringCandidate(
    /** Ключ «категория + место/заметка» — по нему подписку узнают и не предлагают повторно. */
    val key: String,
    val title: String,
    val place: String?,
    val categoryId: Long,
    val subcategoryId: Long?,
    /** Сумма последнего списания: если цена менялась, берём актуальную. */
    val amountKopecks: Long,
    val period: RecurrencePeriod,
    val charges: Int,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val expenseIds: List<Long>,
) {
    val yearlyKopecks: Long get() = period.yearlyKopecks(amountKopecks)
}

/**
 * Автообнаружение регулярных платежей (docs/PLAN.md, этап 4). Чистый код без базы и без ИИ.
 *
 * Правило: траты группируются по «категория + место» (если места нет — по заметке); внутри группы
 * суммы не расходятся больше чем на ±[AMOUNT_TOLERANCE]; интервалы между списаниями все
 * в окне недели (6–8 дней), месяца (28–32) или года (360–370).
 */
object RecurringDetector {
    const val AMOUNT_TOLERANCE = 0.10

    private val WEEK_DAYS = 6L..8L
    private val MONTH_DAYS = 28L..32L
    private val YEAR_DAYS = 360L..370L

    /** Сколько списаний нужно, чтобы считать период устоявшимся. */
    private const val MIN_WEEKLY = 4
    private const val MIN_MONTHLY = 3
    private const val MIN_MONTHLY_RELAXED = 2
    private const val MIN_YEARLY = 2

    private val spaces = Regex("""\s+""")

    /** Имя для ключа: место, а без места — заметка. Пусто — группировать не по чему. */
    internal fun nameKey(place: String?, note: String?): String {
        val p = Places.key(place)
        if (p.isNotEmpty()) return p
        return note.orEmpty().lowercase().replace('ё', 'е').replace(spaces, " ").trim().take(40)
    }

    /** Ключ подписки. Хранится у регулярного платежа и у «это не подписка». */
    fun keyOf(categoryId: Long, place: String?, note: String?): String? {
        val n = nameKey(place, note)
        return if (n.isEmpty()) null else "$categoryId|$n"
    }

    /**
     * @param excludedKeys ключи уже заведённых платежей и отклонённых предложений.
     * @param relaxedCategoryIds категории, где достаточно двух списаний в месяце («Связь и подписки»).
     * @return кандидаты, самые дорогие за год — первыми. Не больше одного кандидата на ключ.
     */
    fun detect(
        expenses: List<ExpenseWithCategory>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        excludedKeys: Set<String> = emptySet(),
        relaxedCategoryIds: Set<Long> = emptySet(),
    ): List<RecurringCandidate> {
        val groups = expenses
            .filterNot { (expense) ->
                expense.source == ExpenseSource.RECONCILE || expense.source == ExpenseSource.DEBT || expense.isPurchaseGoalEntry
            }
            .groupBy { keyOf(it.expense.categoryId, it.expense.place, it.expense.note) }
        val result = mutableListOf<RecurringCandidate>()
        for ((key, items) in groups) {
            if (key == null || key in excludedKeys) continue
            val relaxed = items.first().expense.categoryId in relaxedCategoryIds
            val best = amountClusters(items)
                .mapNotNull { candidateOf(key, it, today, zone, relaxed) }
                .maxWithOrNull(compareBy<RecurringCandidate>({ it.charges }, { it.lastDate }))
            if (best != null) result += best
        }
        return result.sortedWith(compareByDescending<RecurringCandidate> { it.yearlyKopecks }.thenBy { it.title })
    }

    /** Группы трат с близкой суммой: каждая следующая не больше чем на 10% выше самой маленькой в группе. */
    private fun amountClusters(items: List<ExpenseWithCategory>): List<List<ExpenseWithCategory>> {
        val sorted = items.sortedBy { it.expense.amountKopecks }
        val clusters = mutableListOf<MutableList<ExpenseWithCategory>>()
        for (item in sorted) {
            val current = clusters.lastOrNull()
            val base = current?.first()?.expense?.amountKopecks
            if (current != null && base != null && item.expense.amountKopecks <= base * (1 + AMOUNT_TOLERANCE)) {
                current += item
            } else {
                clusters += mutableListOf(item)
            }
        }
        return clusters
    }

    private fun candidateOf(
        key: String,
        cluster: List<ExpenseWithCategory>,
        today: LocalDate,
        zone: ZoneId,
        relaxed: Boolean,
    ): RecurringCandidate? {
        if (cluster.size < 2) return null
        // Одно списание в день: две покупки в один день — не подписка.
        val byDay = cluster
            .sortedBy { it.expense.timestamp }
            .distinctBy { Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() }
        val dates = byDay.map { Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate() }
        if (dates.size < 2) return null
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }

        val period: RecurrencePeriod
        val maxAgeDays: Long
        when {
            gaps.all { it in WEEK_DAYS } && dates.size >= MIN_WEEKLY -> { period = RecurrencePeriod.WEEKLY; maxAgeDays = 10 }
            gaps.all { it in MONTH_DAYS } && dates.size >= (if (relaxed) MIN_MONTHLY_RELAXED else MIN_MONTHLY) -> {
                period = RecurrencePeriod.MONTHLY; maxAgeDays = 45
            }
            gaps.all { it in YEAR_DAYS } && dates.size >= MIN_YEARLY -> { period = RecurrencePeriod.YEARLY; maxAgeDays = 400 }
            else -> return null
        }

        val last = byDay.last()
        val lastDate = dates.last()
        // Давно не списывалось — подписку, видимо, отменили.
        if (ChronoUnit.DAYS.between(lastDate, today) > maxAgeDays) return null

        val e = last.expense
        return RecurringCandidate(
            key = key,
            title = Places.normalize(e.place) ?: e.note.orEmpty().trim().replaceFirstChar { it.uppercaseChar() },
            place = Places.normalize(e.place),
            categoryId = e.categoryId,
            subcategoryId = e.subcategoryId,
            amountKopecks = e.amountKopecks,
            period = period,
            charges = dates.size,
            lastDate = lastDate,
            nextDate = period.advance(lastDate),
            expenseIds = byDay.map { it.expense.id },
        )
    }
}
