package ru.my.traty.data.insights

import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.PeriodComparison
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.period.SpendingPaces
import ru.my.traty.ui.formatRub
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class ObservationKind { FORECAST_HIGH, FORECAST_LOW, CATEGORY_UP, CATEGORY_DOWN, WEEKDAY }

/**
 * Наблюдение для карточки на Главной.
 * [key] стабилен в пределах расчётного периода: по нему запоминается «скрыто».
 */
data class Observation(
    val key: String,
    val kind: ObservationKind,
    val emoji: String,
    val title: String,
    val text: String,
)

/**
 * Что стоит заметить в тратах прямо сейчас. Считается на лету из истории, ничего не хранит.
 * Чистая логика без Android.
 */
object Observations {
    /** Раньше этого дня периода сравнения шумят: пара покупок выглядит как «рост в 5 раз». */
    const val MIN_DAYS_PASSED = 5
    const val FORECAST_MIN_DAYS_PASSED = 7

    /** Заметное изменение категории — не меньше 1 000 ₽ и 25%. */
    const val CATEGORY_MIN_KOPECKS = 100_000L
    const val CATEGORY_MIN_RATIO = 0.25

    const val FORECAST_HIGH = 1.15
    const val FORECAST_LOW = 0.85

    const val WEEKDAY_WEEKS = 8
    const val WEEKDAY_FACTOR = 1.5

    /** Сколько карточек показывать одновременно. */
    const val MAX_SHOWN = 2

    fun build(
        expenses: List<ExpenseWithCategory>,
        today: LocalDate,
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Observation> {
        val rows = expenses.filter { it.expense.source != ExpenseSource.RECONCILE }
        if (rows.isEmpty()) return emptyList()
        fun day(r: ExpenseWithCategory) = Instant.ofEpochMilli(r.expense.timestamp).atZone(zone).toLocalDate()
        val dated = rows.map { it to day(it) }
        val first = dated.minOf { it.second }

        val period = BillingPeriod.containing(today, startDay)
        val previous = period.previous(startDay)
        val passed = (ChronoUnit.DAYS.between(period.startDate, today) + 1).toInt()
        val out = ArrayList<Observation>()

        // Сравнивать нечего, пока нет полного прошлого периода хотя бы с начала
        val hasPrevious = !first.isAfter(previous.startDate.plusDays(3))
        val prevTotal = dated.filter { it.second in previous }.sumOf { it.first.expense.amountKopecks }
        val spent = dated.filter { it.second in period && !it.second.isAfter(today) }.sumOf { it.first.expense.amountKopecks }

        if (hasPrevious && prevTotal > 0 && passed >= FORECAST_MIN_DAYS_PASSED) {
            val pace = SpendingPaces.of(period, today, spent)
            val forecast = pace?.forecast
            if (pace != null && forecast != null && pace.daysLeft > 0) {
                val ratio = forecast.toDouble() / prevTotal
                if (ratio >= FORECAST_HIGH) {
                    val left = prevTotal - spent
                    val tip = if (left > 0) "Чтобы уложиться в ${formatRub(prevTotal)}, остаётся примерно ${formatRub(left / pace.daysLeft)} в день."
                    else "Прошлый итог уже превышен."
                    out += Observation(
                        "forecast:${period.startDate}", ObservationKind.FORECAST_HIGH, "📈",
                        "Темп выше, чем в прошлый раз",
                        "При таком темпе период закончится примерно на ${formatRub(forecast)}, в прошлый раз было ${formatRub(prevTotal)}. $tip",
                    )
                } else if (ratio <= FORECAST_LOW) {
                    out += Observation(
                        "forecast:${period.startDate}", ObservationKind.FORECAST_LOW, "🎯",
                        "Темп лучше, чем в прошлый раз",
                        "При таком темпе выйдет примерно ${formatRub(forecast)} вместо ${formatRub(prevTotal)}. Так держать!",
                    )
                }
            }
        }

        if (hasPrevious && prevTotal > 0 && passed >= MIN_DAYS_PASSED) {
            val (winStart, winEnd) = PeriodComparison.comparableWindow(period, previous, today)
            class Sum(var name: String, var emoji: String, var cur: Long = 0, var prev: Long = 0)
            val byCat = HashMap<Long, Sum>()
            for ((r, d) in dated) {
                val inCur = d in period && !d.isAfter(today)
                val inPrev = !d.isBefore(winStart) && !d.isAfter(winEnd)
                if (!inCur && !inPrev) continue
                val s = byCat.getOrPut(r.expense.categoryId) { Sum(r.categoryName, r.categoryEmoji) }
                if (inCur) s.cur += r.expense.amountKopecks
                if (inPrev) s.prev += r.expense.amountKopecks
            }
            val up = byCat.entries
                .filter { (_, s) -> s.cur - s.prev >= CATEGORY_MIN_KOPECKS && (s.prev == 0L || s.cur >= s.prev * (1 + CATEGORY_MIN_RATIO)) }
                .maxByOrNull { it.value.cur - it.value.prev }
            if (up != null) {
                val s = up.value
                val text = if (s.prev == 0L) {
                    "Уже ${formatRub(s.cur)}, а за тот же срок прошлого периода трат здесь не было."
                } else {
                    val pct = ((s.cur - s.prev) * 100.0 / s.prev).roundToInt()
                    "${formatRub(s.cur)} за $passed ${daysWord(passed)}: на $pct% больше, чем за тот же срок прошлого периода (${formatRub(s.prev)})."
                }
                out += Observation(
                    "up:${up.key}:${period.startDate}", ObservationKind.CATEGORY_UP, s.emoji,
                    if (s.prev == 0L) "Новая статья расходов: «${s.name}»" else "«${s.name}»: тратите больше обычного",
                    text,
                )
            }
            val down = byCat.entries
                .filter { (_, s) -> s.prev - s.cur >= CATEGORY_MIN_KOPECKS && s.cur <= s.prev * (1 - CATEGORY_MIN_RATIO) }
                .maxByOrNull { it.value.prev - it.value.cur }
            if (down != null) {
                val s = down.value
                val pct = ((s.prev - s.cur) * 100.0 / s.prev).roundToInt()
                out += Observation(
                    "down:${down.key}:${period.startDate}", ObservationKind.CATEGORY_DOWN, "👏",
                    "«${s.name}»: тратите меньше",
                    "На $pct% меньше, чем за тот же срок прошлого периода: ${formatRub(s.cur)} вместо ${formatRub(s.prev)}. Экономия ${formatRub(s.prev - s.cur)}.",
                )
            }
        }

        weekday(dated.map { it.first.expense.amountKopecks to it.second }, first, today)?.let { (dow, avg, usual) ->
            out += Observation(
                "weekday:${period.startDate}", ObservationKind.WEEKDAY, "🗓️",
                "${dow.title()} — самый дорогой день",
                "По ${dow.plural()} вы тратите в среднем ${formatRub(avg)}, в остальные дни около ${formatRub(usual)}.",
            )
        }
        return out
    }

    /** Показываемые карточки: без скрытых, не больше [MAX_SHOWN], в порядке важности. */
    fun visible(all: List<Observation>, dismissed: Set<String>): List<Observation> =
        all.filter { it.key !in dismissed }.take(MAX_SHOWN)

    private data class Weekday(val dow: DayOfWeek, val avg: Long, val usual: Long)

    /** День недели, по которому тратят в [WEEKDAY_FACTOR] раза больше, чем в остальные, за последние [WEEKDAY_WEEKS] недель. */
    private fun weekday(items: List<Pair<Long, LocalDate>>, first: LocalDate, today: LocalDate): Weekday? {
        val from = today.minusDays(WEEKDAY_WEEKS * 7L)
        if (first.isAfter(from)) return null
        val perDow = LongArray(7)
        for ((sum, d) in items) if (!d.isBefore(from) && d.isBefore(today)) perDow[d.dayOfWeek.value - 1] += sum
        val avg = perDow.map { it / WEEKDAY_WEEKS }
        val top = avg.indices.maxByOrNull { avg[it] } ?: return null
        val others = avg.filterIndexed { i, _ -> i != top }.average().toLong()
        if (others < 10_000 || avg[top] < others * WEEKDAY_FACTOR) return null
        return Weekday(DayOfWeek.of(top + 1), avg[top], others)
    }

    private fun DayOfWeek.title() = when (this) {
        DayOfWeek.MONDAY -> "Понедельник"
        DayOfWeek.TUESDAY -> "Вторник"
        DayOfWeek.WEDNESDAY -> "Среда"
        DayOfWeek.THURSDAY -> "Четверг"
        DayOfWeek.FRIDAY -> "Пятница"
        DayOfWeek.SATURDAY -> "Суббота"
        DayOfWeek.SUNDAY -> "Воскресенье"
    }

    private fun DayOfWeek.plural() = when (this) {
        DayOfWeek.MONDAY -> "понедельникам"
        DayOfWeek.TUESDAY -> "вторникам"
        DayOfWeek.WEDNESDAY -> "средам"
        DayOfWeek.THURSDAY -> "четвергам"
        DayOfWeek.FRIDAY -> "пятницам"
        DayOfWeek.SATURDAY -> "субботам"
        DayOfWeek.SUNDAY -> "воскресеньям"
    }

    private fun daysWord(n: Int) = if (n % 10 == 1 && n % 100 != 11) "день" else if (n % 10 in 2..4 && n % 100 !in 12..14) "дня" else "дней"
}
