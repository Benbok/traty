package ru.my.traty.data.budget

import java.util.Locale

/** План (категории, подкатегории или общий лимит), который проверяется на пороги 80 % и 100 %. */
data class AlertTarget(
    /** Постоянный ключ плана внутри месяца: «c5», «c5s7», «total». */
    val id: String,
    /** Как называть план в уведомлении: «Кафе», «Кафе · Кофе», «Общий лимит». */
    val name: String,
    val planned: Long,
    val spent: Long,
)

/** Уведомление о плане: [key] запоминается, чтобы не показывать его повторно. */
data class BudgetAlert(val key: String, val targetId: String, val title: String, val text: String)

/**
 * Уведомления «потрачено 80 % плана» и «план выбран / превышен» (этап 8).
 * Каждый порог срабатывает один раз за месяц плана. Если траты сразу перескочили 100 %,
 * приходит одно уведомление о превышении, а не два подряд.
 */
object BudgetAlerts {
    const val NEAR_PERCENT = PlanProgresses.NEAR_PERCENT
    const val FULL_PERCENT = 100

    private val RU: Locale = Locale.forLanguageTag("ru")

    fun key(month: Int, targetId: String, percent: Int) = "$month|$targetId|$percent"

    /** Ключи, которые относятся к месяцу [month]: остальные можно забыть. */
    fun keepForMonth(sent: Set<String>, month: Int): Set<String> = sent.filterTo(HashSet()) { it.startsWith("$month|") }

    /**
     * Какие уведомления показать сейчас. Вызывающий запоминает [BudgetAlert.key] показанных
     * и [alsoMark] (пропущенный порог 80 %, когда сразу пришло 100 %).
     */
    fun due(month: Int, targets: List<AlertTarget>, sent: Set<String>): Due {
        val show = ArrayList<BudgetAlert>()
        val mark = HashSet<String>()
        for (t in targets) {
            if (t.planned <= 0) continue
            val nearKey = key(month, t.id, NEAR_PERCENT)
            val fullKey = key(month, t.id, FULL_PERCENT)
            when {
                t.spent >= t.planned -> if (fullKey !in sent) {
                    show += full(fullKey, t)
                    if (nearKey !in sent) mark += nearKey
                }
                t.spent * 100 >= t.planned * NEAR_PERCENT -> if (nearKey !in sent) show += near(nearKey, t)
            }
        }
        return Due(show, mark)
    }

    /**
     * Что проверять в месяце [month]: действующие планы категорий и подкатегорий и общий лимит.
     * [nameOf] даёт подпись плана; null — категория в архиве, её план не проверяем.
     */
    fun targets(
        budgets: List<Budget>,
        totals: List<MonthBudget>,
        month: Int,
        spends: List<PlanSpend>,
        nameOf: (PlanKey) -> String?,
    ): List<AlertTarget> {
        val spent = BudgetPlans.spent(spends)
        val out = BudgetPlans.effective(budgets, month).mapNotNull { b ->
            val name = nameOf(b.key) ?: return@mapNotNull null
            val id = "c${b.categoryId}" + (b.subcategoryId?.let { "s$it" } ?: "")
            AlertTarget(id, name, b.amountKopecks, spent[b.key] ?: 0L)
        }
        val total = TotalBudgets.effective(totals, month) ?: return out
        return out + AlertTarget(TOTAL_ID, "Общий лимит", total.amountKopecks, spends.sumOf { it.amountKopecks })
    }

    const val TOTAL_ID = "total"

    data class Due(val alerts: List<BudgetAlert>, val alsoMark: Set<String>)

    private fun near(key: String, t: AlertTarget): BudgetAlert {
        val percent = (t.spent * 100 / t.planned).toInt()
        return BudgetAlert(
            key, t.id,
            title = "${t.name}: потрачено $percent % плана",
            text = "Осталось ${rub(t.planned - t.spent)} из ${rub(t.planned)} до конца периода.",
        )
    }

    private fun full(key: String, t: AlertTarget): BudgetAlert = if (t.spent == t.planned) {
        BudgetAlert(key, t.id, "${t.name}: план выбран", "Потрачено ${rub(t.spent)} из ${rub(t.planned)}. Дальше будет перерасход.")
    } else {
        BudgetAlert(
            key, t.id,
            title = "${t.name}: план превышен",
            text = "Потрачено ${rub(t.spent)} из ${rub(t.planned)}, перерасход ${rub(t.spent - t.planned)}.",
        )
    }

    internal fun rub(kopecks: Long): String = String.format(RU, "%,d ₽", Math.round(kopecks / 100.0))
}
