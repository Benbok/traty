package ru.my.traty.data.weekly

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Трата для заданий: только то, что нужно для расчёта. */
data class TaskSpend(val categoryId: Long, val amountKopecks: Long, val date: LocalDate)

/** Категория, из которой можно сделать задание. */
data class TaskCategory(val id: Long, val name: String, val impulsive: Boolean)

/** Сводка по подпискам для задания «пересмотреть подписки». */
data class SubscriptionsSummary(val count: Int, val monthlyKopecks: Long)

object Weeks {
    /** Понедельник недели, в которую попадает [date]. */
    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/**
 * Предложения заданий на неделю по истории трат (docs/PLAN.md, этап 8). Чистый код:
 * смотрит на [HISTORY_WEEKS] полных недель до [weekStart] и предлагает задания чуть строже обычного.
 */
object WeeklyTaskGenerator {
    const val HISTORY_WEEKS = 8
    /** Меньше стольких недель истории — заданий не предлагаем: «обычное» ещё не понятно. */
    const val MIN_WEEKS = 3
    const val MAX_TASKS = 3
    /** Лимит предлагаем для категорий от 1 000 ₽ в неделю. */
    const val MIN_LIMIT_KOPECKS = 100_000L
    const val LIMIT_FACTOR = 0.8
    const val COUNT_FACTOR = 0.7
    const val MIN_SUBSCRIPTIONS = 3

    private val RU: Locale = Locale.forLanguageTag("ru")

    private data class Stat(val category: TaskCategory, val weeks: Int, val avgKopecks: Long, val avgCount: Double, val avgDays: Double)

    /** Задания на неделю: не больше [MAX_TASKS], все про разные категории. */
    fun generate(
        weekStart: LocalDate,
        history: List<TaskSpend>,
        categories: List<TaskCategory>,
        subscriptions: SubscriptionsSummary? = null,
    ): List<WeeklyTask> {
        val groups = byType(weekStart, history, categories, subscriptions)
        val out = ArrayList<WeeklyTask>()
        fun free(t: WeeklyTask) = t.categoryId == null || out.none { it.categoryId == t.categoryId }
        // Сначала по одному заданию каждого вида, каждое про свою категорию, потом добираем запасными
        for (g in groups) {
            if (out.size >= MAX_TASKS) break
            g.firstOrNull(::free)?.let { out += it }
        }
        for (t in groups.flatten()) {
            if (out.size >= MAX_TASKS) break
            if (t !in out && free(t)) out += t
        }
        return out
    }

    /** Замена задания: первое предложение, которого ещё нет на этой неделе и чья категория свободна. */
    fun replacement(
        weekStart: LocalDate,
        week: List<WeeklyTask>,
        history: List<TaskSpend>,
        categories: List<TaskCategory>,
        subscriptions: SubscriptionsSummary? = null,
    ): WeeklyTask? {
        val busy = week.filter { it.active }.mapNotNull { it.categoryId }.toSet()
        val seen = week.map { it.type to it.categoryId }.toSet()
        return ranked(weekStart, history, categories, subscriptions).firstOrNull { c ->
            (c.type to c.categoryId) !in seen && (c.categoryId == null || c.categoryId !in busy)
        }
    }

    /** Все предложения по порядку: сначала лучшие каждого вида, затем запасные. */
    internal fun ranked(
        weekStart: LocalDate,
        history: List<TaskSpend>,
        categories: List<TaskCategory>,
        subscriptions: SubscriptionsSummary?,
    ): List<WeeklyTask> {
        val groups = byType(weekStart, history, categories, subscriptions)
        return groups.mapNotNull { it.firstOrNull() } + groups.flatMap { it.drop(1) }
    }

    /** Предложения, сгруппированные по виду: лимит, дни без трат, число трат, подписки. */
    private fun byType(
        weekStart: LocalDate,
        history: List<TaskSpend>,
        categories: List<TaskCategory>,
        subscriptions: SubscriptionsSummary?,
    ): List<List<WeeklyTask>> {
        val start = Weeks.monday(weekStart)
        val stats = stats(start, history, categories)
        val impulsiveFirst = compareByDescending<Stat> { it.category.impulsive }
        val limits = stats.filter { it.avgKopecks >= MIN_LIMIT_KOPECKS }
            .sortedWith(impulsiveFirst.thenByDescending { it.avgKopecks })
            .map { limit(start, it) }
        val freeDays = stats.filter { it.avgDays in 2.0..5.5 }
            .sortedWith(impulsiveFirst.thenByDescending { it.avgDays })
            .mapNotNull { noSpend(start, it) }
        val counts = stats.filter { it.avgCount >= 3.0 }
            .sortedWith(impulsiveFirst.thenByDescending { it.avgCount })
            .mapNotNull { count(start, it) }
        val review = listOfNotNull(subscriptions?.takeIf { it.count >= MIN_SUBSCRIPTIONS }?.let { review(start, it) })
        return listOf(limits, freeDays, counts, review)
    }

    private fun stats(start: LocalDate, history: List<TaskSpend>, categories: List<TaskCategory>): List<Stat> {
        val from = start.minusWeeks(HISTORY_WEEKS.toLong())
        val window = history.filter { !it.date.isBefore(from) && it.date.isBefore(start) }
        val first = history.minOfOrNull { it.date } ?: return emptyList()
        // Недели до первой траты в истории не считаются, чтобы новая история не занижала средние
        val weeks = minOf(HISTORY_WEEKS.toLong(), ChronoUnit.WEEKS.between(Weeks.monday(first), start)).toInt()
        if (weeks < MIN_WEEKS) return emptyList()
        val byCat = window.groupBy { it.categoryId }
        return categories.mapNotNull { c ->
            val rows = byCat[c.id] ?: return@mapNotNull null
            val activeWeeks = rows.map { Weeks.monday(it.date) }.distinct().size
            if (activeWeeks * 2 < weeks) return@mapNotNull null
            Stat(
                category = c,
                weeks = weeks,
                avgKopecks = rows.sumOf { it.amountKopecks } / weeks,
                avgCount = rows.size.toDouble() / weeks,
                avgDays = rows.map { it.date }.distinct().size.toDouble() / weeks,
            )
        }
    }

    private fun limit(start: LocalDate, s: Stat): WeeklyTask {
        val target = ((s.avgKopecks * LIMIT_FACTOR).toLong() / 10_000 * 10_000).coerceAtLeast(10_000)
        return WeeklyTask(
            weekStart = start.toEpochDay(), type = WeeklyTaskType.CATEGORY_LIMIT.name, categoryId = s.category.id,
            title = "«${s.category.name}» — не больше ${rub(target)} (обычно ${rub(roundRub(s.avgKopecks))})",
            target = target, usual = s.avgKopecks, usualKopecks = s.avgKopecks,
        )
    }

    private fun noSpend(start: LocalDate, s: Stat): WeeklyTask? {
        val usualFree = (7 - s.avgDays).roundToLong().coerceIn(0, 7)
        val target = minOf(6L, 7L - ceil(s.avgDays).toLong() + 1)
        if (target <= usualFree) return null
        return WeeklyTask(
            weekStart = start.toEpochDay(), type = WeeklyTaskType.NO_SPEND_DAYS.name, categoryId = s.category.id,
            title = "«${s.category.name}» — $target ${daysWord(target)} без трат (обычно $usualFree)",
            target = target, usual = usualFree, usualKopecks = s.avgKopecks,
        )
    }

    private fun count(start: LocalDate, s: Stat): WeeklyTask? {
        val usual = s.avgCount.roundToLong()
        val target = (s.avgCount * COUNT_FACTOR).toLong().coerceAtLeast(1)
        if (target >= usual) return null
        return WeeklyTask(
            weekStart = start.toEpochDay(), type = WeeklyTaskType.COUNT_LIMIT.name, categoryId = s.category.id,
            title = "«${s.category.name}» — не больше $target ${timesWord(target)} (обычно $usual)",
            target = target, usual = usual, usualKopecks = s.avgKopecks,
        )
    }

    private fun review(start: LocalDate, s: SubscriptionsSummary) = WeeklyTask(
        weekStart = start.toEpochDay(), type = WeeklyTaskType.REVIEW_SUBSCRIPTIONS.name,
        title = "Пересмотреть подписки: ${s.count} на ${rub(s.monthlyKopecks)} в месяц",
        target = s.count.toLong(), usual = s.count.toLong(),
    )

    private fun roundRub(kopecks: Long) = (kopecks + 5_000) / 10_000 * 10_000

    internal fun rub(kopecks: Long): String = String.format(RU, "%,d ₽", Math.round(kopecks / 100.0))

    internal fun daysWord(n: Long): String = plural(n, "день", "дня", "дней")

    /** После «не больше»: «1 раза», «2 раз», «5 раз». */
    internal fun timesWord(n: Long): String = if (n % 10 == 1L && n % 100 != 11L) "раза" else "раз"

    private fun plural(n: Long, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1L -> one
            m10 in 2..4 -> few
            else -> many
        }
    }
}
