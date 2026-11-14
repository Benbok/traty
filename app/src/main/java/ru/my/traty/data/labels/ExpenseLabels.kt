package ru.my.traty.data.labels

import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.recurring.RecurringCandidate
import ru.my.traty.data.recurring.RecurringDetector
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Метка на трате. Порядок — порядок показа. */
enum class ExpenseLabel(val emoji: String, val title: String) {
    LARGE("🔺", "Необычно крупная"),
    IMPULSIVE("⚡", "Импульсивная"),
    FREQUENT("🔁", "Частая"),
    SUBSCRIPTION("📅", "Похожа на подписку"),
    NEW_PLACE("🆕", "Новое место"),
}

/** Метка и пояснение, почему она поставлена («в 4,2 раза больше обычной…»). */
data class ExpenseLabelHit(val label: ExpenseLabel, val reason: String)

/**
 * Метки на тратах (docs/PLAN.md, этап 5). Чистый код: считается по истории трат, в базе ничего не хранится,
 * поэтому правка или удаление траты сразу пересчитывает метки соседей, а пороги можно менять без миграций.
 *
 * Пока в приложении меньше [MIN_HISTORY_DAYS] дней истории до траты, метки «крупная», «частая» и «новое место»
 * не ставятся: иначе «новым» было бы всё, а «крупным» первая же покупка.
 */
object ExpenseLabeler {
    const val MIN_HISTORY_DAYS = 30
    const val LOOKBACK_DAYS = 90
    /** Минимум трат в выборке, чтобы сравнивать с «обычным». */
    const val MIN_SAMPLE = 5

    const val LARGE_FACTOR = 3.0
    /** Меньше этой суммы (300 ₽) трата «крупной» не бывает, как бы мала ни была медиана. */
    const val LARGE_MIN_KOPECKS = 30_000L

    const val FREQUENT_FACTOR = 1.5
    const val FREQUENT_MIN_COUNT = 3

    /** «Мелкая» трата для правила «3 и больше за день» — до 500 ₽. */
    const val SMALL_KOPECKS = 50_000L
    const val SMALL_DAY_COUNT = 3
    const val NIGHT_FROM_HOUR = 22
    const val NIGHT_TO_HOUR = 4

    /** Категории, где вероятны импульсивные траты. Пока по названию; позже станет настройкой категории. */
    val IMPULSIVE_CATEGORIES = listOf("Кафе и рестораны", "Доставка еды", "Развлечения", "Одежда и обувь")

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val RU: Locale = Locale.forLanguageTag("ru")

    /**
     * @param subscriptionExpenseIds траты, похожие на подписку (см. [subscriptionExpenseIds]).
     * @return id траты → метки; у трат без меток записи нет.
     */
    fun label(
        expenses: List<ExpenseWithCategory>,
        subscriptionExpenseIds: Set<Long> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Long, List<ExpenseLabelHit>> {
        val rows = expenses
            .filter { it.expense.source != ExpenseSource.RECONCILE }
            .sortedWith(compareBy({ it.expense.timestamp }, { it.expense.id }))
        if (rows.isEmpty()) return emptyMap()
        val firstTs = rows.first().expense.timestamp

        // Группы для сравнения: подкатегория (если есть) и категория целиком
        val bySub = rows.filter { it.expense.subcategoryId != null }.groupBy { it.expense.subcategoryId }
        val byCat = rows.groupBy { it.expense.categoryId }
        val indexInSub = HashMap<Long, Int>()
        val indexInCat = HashMap<Long, Int>()
        bySub.values.forEach { g -> g.forEachIndexed { i, r -> indexInSub[r.expense.id] = i } }
        byCat.values.forEach { g -> g.forEachIndexed { i, r -> indexInCat[r.expense.id] = i } }

        // Первая трата в каждом месте
        val firstAtPlace = HashMap<String, Long>()
        for (r in rows) {
            val k = Places.key(r.expense.place)
            if (k.isNotEmpty()) firstAtPlace.putIfAbsent(k, r.expense.id)
        }

        // Мелкие траты за один день в одной подкатегории (категории без подкатегории — вместе)
        val smallPerDay = rows
            .filter { it.expense.amountKopecks <= SMALL_KOPECKS }
            .groupingBy { Triple(it.expense.categoryId, it.expense.subcategoryId, dayOf(it.expense.timestamp, zone)) }
            .eachCount()

        val result = HashMap<Long, List<ExpenseLabelHit>>()
        for (r in rows) {
            val e = r.expense
            val hits = mutableListOf<ExpenseLabelHit>()
            val hasHistory = e.timestamp - firstTs >= MIN_HISTORY_DAYS * DAY_MS

            if (hasHistory && e.amountKopecks >= LARGE_MIN_KOPECKS) {
                val sample = sampleBefore(e.subcategoryId?.let { bySub[it] }, indexInSub[e.id], r)
                    .takeIf { it.size >= MIN_SAMPLE }
                    ?: sampleBefore(byCat[e.categoryId], indexInCat[e.id], r)
                val median = median(sample)
                if (sample.size >= MIN_SAMPLE && median > 0 && e.amountKopecks > LARGE_FACTOR * median) {
                    val ratio = e.amountKopecks.toDouble() / median
                    hits += ExpenseLabelHit(
                        ExpenseLabel.LARGE,
                        "Сумма ${rub(e.amountKopecks)} — ${times(ratio)} больше обычной для «${groupName(r)}»: " +
                            "медиана за $LOOKBACK_DAYS дней ${rub(median)}.",
                    )
                }
            }

            if (isImpulsiveCategory(r.categoryName)) {
                val hour = Instant.ofEpochMilli(e.timestamp).atZone(zone).hour
                val night = hour >= NIGHT_FROM_HOUR || hour < NIGHT_TO_HOUR
                val small = smallPerDay[Triple(e.categoryId, e.subcategoryId, dayOf(e.timestamp, zone))] ?: 0
                if (night) {
                    val time = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalTime()
                    hits += ExpenseLabelHit(
                        ExpenseLabel.IMPULSIVE,
                        "Покупка поздно вечером или ночью (%02d:%02d) в категории «${r.categoryName}».".format(time.hour, time.minute),
                    )
                } else if (e.amountKopecks <= SMALL_KOPECKS && small >= SMALL_DAY_COUNT) {
                    hits += ExpenseLabelHit(
                        ExpenseLabel.IMPULSIVE,
                        "$small ${plural(small, "мелкая трата", "мелкие траты", "мелких трат")} в «${groupName(r)}» за один день.",
                    )
                }
            }

            if (hasHistory) {
                val weekMs = 7 * DAY_MS
                fun frequency(group: List<ExpenseWithCategory>?, index: Int?): Triple<Int, Int, Double>? {
                    if (group == null || index == null) return null
                    var recent = 0
                    var baseline = 0
                    var i = index
                    while (i >= 0 && e.timestamp - group[i].expense.timestamp <= LOOKBACK_DAYS * DAY_MS) {
                        val age = e.timestamp - group[i].expense.timestamp
                        if (age < weekMs) recent++ else baseline++
                        i--
                    }
                    val perWeek = baseline / ((LOOKBACK_DAYS - 7) / 7.0)
                    return Triple(recent, baseline, perWeek)
                }
                val sub = frequency(e.subcategoryId?.let { bySub[it] }, indexInSub[e.id])?.takeIf { it.second >= MIN_SAMPLE }
                val f = sub ?: frequency(byCat[e.categoryId], indexInCat[e.id])
                if (f != null && f.second >= MIN_SAMPLE && f.first >= FREQUENT_MIN_COUNT && f.first > FREQUENT_FACTOR * f.third) {
                    hits += ExpenseLabelHit(
                        ExpenseLabel.FREQUENT,
                        "${f.first} ${plural(f.first, "трата", "траты", "трат")} в «${groupName(r)}» за 7 дней, " +
                            "обычно около ${oneDecimal(f.third)} в неделю.",
                    )
                }
            }

            if (e.id in subscriptionExpenseIds) {
                hits += ExpenseLabelHit(
                    ExpenseLabel.SUBSCRIPTION,
                    if (e.source == ExpenseSource.RECURRING) "Платёж из раздела «Подписки», отмечен как «Списалось»."
                    else "Повторяется с тем же местом и почти той же суммой раз в неделю, месяц или год.",
                )
            }

            val placeKey = Places.key(e.place)
            if (hasHistory && placeKey.isNotEmpty() && firstAtPlace[placeKey] == e.id) {
                hits += ExpenseLabelHit(ExpenseLabel.NEW_PLACE, "Первая трата в «${Places.normalize(e.place)}».")
            }

            if (hits.isNotEmpty()) result[e.id] = hits.sortedBy { it.label.ordinal }
        }
        return result
    }

    /**
     * Траты, похожие на подписку: те, что нашёл поиск повторов, трата из «Списалось» и траты, чьё место
     * (или заметка/подкатегория) совпало с заведённым платежом.
     */
    fun subscriptionExpenseIds(
        expenses: List<ExpenseWithCategory>,
        recurring: List<Recurring>,
        candidates: List<RecurringCandidate>,
    ): Set<Long> {
        val keys = recurring.filter { it.state != RecurringState.DISMISSED }.mapNotNull { it.detectKey }.toSet()
        val ids = HashSet<Long>()
        candidates.forEach { ids += it.expenseIds }
        for (r in expenses) {
            val e = r.expense
            if (e.source == ExpenseSource.RECURRING) {
                ids += e.id
                continue
            }
            if (keys.isEmpty()) continue
            val label = e.note?.trim()?.takeIf { it.isNotEmpty() } ?: r.subcategoryName?.trim()?.takeIf { it.isNotEmpty() } ?: r.categoryName
            val key = RecurringDetector.keyOf(e.categoryId, e.place, label)
            if (key != null && key in keys) ids += e.id
        }
        return ids
    }

    /** Суммы трат группы за [LOOKBACK_DAYS] дней до траты [r] (саму трату и более поздние не берём). */
    private fun sampleBefore(group: List<ExpenseWithCategory>?, index: Int?, r: ExpenseWithCategory): List<Long> {
        if (group == null || index == null) return emptyList()
        val out = ArrayList<Long>()
        var i = index - 1
        while (i >= 0 && r.expense.timestamp - group[i].expense.timestamp <= LOOKBACK_DAYS * DAY_MS) {
            out += group[i].expense.amountKopecks
            i--
        }
        return out
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun isImpulsiveCategory(name: String) = IMPULSIVE_CATEGORIES.any { it.equals(name, ignoreCase = true) }

    private fun groupName(r: ExpenseWithCategory) = r.subcategoryName?.let { "${r.categoryName} → $it" } ?: r.categoryName

    private fun dayOf(ts: Long, zone: ZoneId) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().toEpochDay()

    private fun rub(kopecks: Long): String = String.format(RU, "%,d ₽", Math.round(kopecks / 100.0))

    private fun oneDecimal(v: Double): String = String.format(RU, "%.1f", v)

    /** «в 4,2 раза», «в 5 раз». */
    private fun times(ratio: Double): String {
        val rounded = Math.round(ratio * 10) / 10.0
        return if (rounded % 1.0 == 0.0) {
            val n = rounded.toInt()
            "в $n ${if (n in 2..4) "раза" else "раз"}"
        } else {
            "в ${oneDecimal(rounded)} раза"
        }
    }

    internal fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }
}
