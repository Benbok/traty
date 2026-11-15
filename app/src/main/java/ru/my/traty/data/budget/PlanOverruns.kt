package ru.my.traty.data.budget

import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.util.Locale

/** Трата для поиска перерасхода: как [PlanSpend], но с id и временем, чтобы идти по порядку. */
data class OverrunSpend(
    val id: Long,
    val categoryId: Long,
    val subcategoryId: Long?,
    val amountKopecks: Long,
    val date: LocalDate,
    val timestamp: Long,
)

/**
 * Траты сверх плана (этап 8, для метки ⚡): траты, после которых план категории, подкатегории
 * или общий лимит месяца оказался превышен. Считается по каждому расчётному периоду отдельно,
 * по порядку трат внутри него.
 */
object PlanOverruns {
    private val RU: Locale = Locale.forLanguageTag("ru")

    /** id траты → пояснение («Трата сверх плана: с ней потрачено 10 400 ₽ из 10 000 ₽.»). */
    fun find(
        spends: List<OverrunSpend>,
        budgets: List<Budget>,
        totals: List<MonthBudget>,
        startDay: Int,
    ): Map<Long, String> {
        if (spends.isEmpty() || (budgets.isEmpty() && totals.isEmpty())) return emptyMap()
        val out = HashMap<Long, String>()
        val byMonth = spends.groupBy { PlanMonth.of(BillingPeriod.containing(it.date, startDay)) }
        for ((month, list) in byMonth) {
            val plans = BudgetPlans.effective(budgets, month).associateBy { it.key }
            val total = TotalBudgets.effective(totals, month)
            if (plans.isEmpty() && total == null) continue
            val running = HashMap<PlanKey, Long>()
            var all = 0L
            for (s in list.sortedWith(compareBy({ it.timestamp }, { it.id }))) {
                val keys = listOfNotNull(s.subcategoryId?.let { PlanKey(s.categoryId, it) }, PlanKey(s.categoryId))
                var reason: String? = null
                for (k in keys) {
                    val after = running.merge(k, s.amountKopecks, Long::plus) ?: s.amountKopecks
                    val plan = plans[k] ?: continue
                    if (reason == null && after > plan.amountKopecks) reason = "Трата сверх плана: с ней потрачено ${rub(after)} из ${rub(plan.amountKopecks)}."
                }
                all += s.amountKopecks
                if (reason == null && total != null && all > total.amountKopecks) {
                    reason = "Трата сверх общего лимита на месяц: с ней потрачено ${rub(all)} из ${rub(total.amountKopecks)}."
                }
                if (reason != null) out[s.id] = reason
            }
        }
        return out
    }

    private fun rub(kopecks: Long): String = String.format(RU, "%,d ₽", Math.round(kopecks / 100.0))
}
