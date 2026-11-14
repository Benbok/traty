package ru.my.traty.data.digest

import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.ui.formatRub
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Настроение вечернего итога: от него зависят заголовок и поддерживающая фраза. */
enum class DigestTone { NO_SPENDING, LOWER, USUAL, HIGHER, MUCH_HIGHER, NO_BASELINE }

/** Готовый текст уведомления «Итог дня». */
data class DailyDigest(val tone: DigestTone, val title: String, val body: String)

/**
 * Вечерний итог дня: сколько потрачено сегодня, как это соотносится с обычным днём и что поступило.
 * Чистая логика без Android — собирает текст, а показывает его воркер.
 */
object DailyDigests {
    /** Сколько прошлых дней берём для «обычного» дня. */
    const val BASELINE_DAYS = 28

    /** Меньше стольких дней истории — сравнивать не с чем. */
    const val MIN_BASELINE_DAYS = 14

    /** Если обычный день дешевле 100 ₽, сравнение теряет смысл. */
    const val MIN_BASELINE_KOPECKS = 10_000L

    const val LOWER_BELOW = 0.7
    const val HIGHER_FROM = 1.3
    const val MUCH_HIGHER_FROM = 2.0

    /**
     * @param expenses вся история трат (хотя бы за [BASELINE_DAYS] дней и сегодня); записи сверки не считаются.
     * @param incomes поступления; записи сверки не считаются.
     * @return null, если в приложении ещё нет ни одной траты (нечем делиться).
     */
    fun build(
        expenses: List<ExpenseWithCategory>,
        incomes: List<Income>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyDigest? {
        val rows = expenses.filter { it.expense.source != ExpenseSource.RECONCILE }
        if (rows.isEmpty()) return null

        fun day(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

        val todayRows = rows.filter { day(it.expense.timestamp) == today }
        val spent = todayRows.sumOf { it.expense.amountKopecks }
        val received = incomes
            .filter { it.origin != IncomeOrigin.RECONCILE && day(it.timestamp) == today }
            .sumOf { it.amountKopecks }

        val usual = usualDay(rows.map { it.expense }, today, zone)
        val variant = (today.toEpochDay() % 2).toInt()

        val tone = when {
            spent == 0L -> DigestTone.NO_SPENDING
            usual == null -> DigestTone.NO_BASELINE
            spent < usual * LOWER_BELOW -> DigestTone.LOWER
            spent >= usual * MUCH_HIGHER_FROM -> DigestTone.MUCH_HIGHER
            spent >= usual * HIGHER_FROM -> DigestTone.HIGHER
            else -> DigestTone.USUAL
        }

        val lines = ArrayList<String>()
        val title: String
        when (tone) {
            DigestTone.NO_SPENDING -> {
                title = "Сегодня трат нет"
                lines += "Если ничего не покупали, это здорово. Если что-то забыли записать, загляните в приложение."
            }
            DigestTone.LOWER -> {
                title = if (variant == 0) "Ого, сегодня потратили меньше обычного! 👏" else "Отличный день: расходов меньше, чем обычно 🙌"
                lines += "${formatRub(spent)} вместо обычных ${formatRub(usual!!)}. Так держать!"
            }
            DigestTone.USUAL -> {
                title = if (variant == 0) "Ровный день: ${formatRub(spent)} 👌" else "Сегодня всё как обычно: ${formatRub(spent)}"
                lines += "Обычный день у вас выходит примерно на ${formatRub(usual!!)}."
            }
            DigestTone.HIGHER -> {
                title = if (variant == 0) "Оу… сегодня трат больше обычного" else "Сегодня потратили чуть больше обычного"
                lines += "${formatRub(spent)} при обычных ${formatRub(usual!!)}. Бывает, завтра можно выровнять 💪"
            }
            DigestTone.MUCH_HIGHER -> {
                val times = (spent.toDouble() / usual!!).roundToInt()
                title = if (variant == 0) "Ух, сегодня дорогой день 😅" else "Оу.., сегодня траты оказались заметно больше"
                lines += "${formatRub(spent)}: это в $times ${timesWord(times)} больше обычного. Один такой день бюджет не ломает."
            }
            DigestTone.NO_BASELINE -> {
                title = "Итог дня: ${formatRub(spent)}"
                lines += "Накопим немного записей, и я начну сравнивать с обычными днями."
            }
        }

        topCategory(todayRows)?.let { (name, emoji, sum) ->
            if (spent > 0 && todayRows.map { it.expense.categoryId }.distinct().size > 1) {
                lines += "Больше всего ушло на $emoji $name: ${formatRub(sum)}."
            }
        }
        if (received > 0) lines += "Поступило: ${formatRub(received)}."
        if (received > 0 && spent > 0) {
            val net = received - spent
            lines += if (net >= 0) "За день в плюсе на ${formatRub(net)}." else "Расходов больше поступлений на ${formatRub(-net)}."
        }
        return DailyDigest(tone, title, lines.joinToString("\n"))
    }

    /**
     * «Обычный день» — медиана суточных трат за предыдущие дни (дни без трат считаются нулями).
     * null — истории меньше [MIN_BASELINE_DAYS] дней или обычный день слишком дёшев для сравнения.
     * Медиана, а не среднее: одна дорогая покупка не должна сдвигать «обычное».
     */
    fun usualDay(expenses: List<Expense>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val spending = expenses.filter { it.source != ExpenseSource.RECONCILE }
        if (spending.isEmpty()) return null
        val byDay = HashMap<LocalDate, Long>()
        for (e in spending) {
            val d = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
            byDay[d] = (byDay[d] ?: 0L) + e.amountKopecks
        }
        val first = byDay.keys.min()
        val available = java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt()
        val days = minOf(BASELINE_DAYS, available)
        if (days < MIN_BASELINE_DAYS) return null
        val totals = (1..days).map { byDay[today.minusDays(it.toLong())] ?: 0L }.sorted()
        val median = if (totals.size % 2 == 1) totals[totals.size / 2] else (totals[totals.size / 2 - 1] + totals[totals.size / 2]) / 2
        return median.takeIf { it >= MIN_BASELINE_KOPECKS }
    }

    private fun topCategory(rows: List<ExpenseWithCategory>): Triple<String, String, Long>? =
        rows.groupBy { it.expense.categoryId }
            .map { (_, v) -> Triple(v.first().categoryName, v.first().categoryEmoji, v.sumOf { it.expense.amountKopecks }) }
            .maxByOrNull { it.third }

    private fun timesWord(n: Int) = if (n % 10 in 2..4 && n % 100 !in 12..14) "раза" else "раз"
}
