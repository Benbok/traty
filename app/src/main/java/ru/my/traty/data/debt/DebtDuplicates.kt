package ru.my.traty.data.debt

import ru.my.traty.data.Places
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import kotlin.math.abs

/**
 * Кредит или рассрочка, заведённые ещё и подпиской. «Уже обещано» плана такую подписку не считает,
 * карточка долгов о ней предупреждает (напоминаний приходит два). Ищет приложение по сумме платежа и признакам покупки.
 *
 * Подписка похожа на платёж покупки, если сумма почти та же (банк округляет по-своему: допуск 2 %,
 * но не меньше 50 ₽) и совпадает хоть что-то ещё: слово в названии, магазин, категория покупки
 * или категория «Долги и кредиты». У покупки без графика регулярного платежа нет — и дубля тоже.
 */
object DebtDuplicates {
    private const val MIN_TOLERANCE_KOPECKS = 5_000L

    /**
     * Дубли для всех открытых покупок «Я должен»: id покупки → её подписка-дубль. Каждая активная подписка
     * достаётся не больше чем одной покупке. Одни и те же правила для карточки долгов и «Уже обещано».
     */
    fun matchAll(debts: List<Debt>, recurring: List<Recurring>, loanCategoryId: Long? = null): Map<Long, Recurring> {
        val free = recurring.filter { it.state == RecurringState.ACTIVE }.toMutableList()
        val result = mutableMapOf<Long, Recurring>()
        for (debt in debts) {
            if (debt.state != DebtState.ACTIVE || debt.direction != DebtDirection.I_OWE) continue
            val dup = find(debt, free, loanCategoryId) ?: continue
            result[debt.id] = dup
            free.remove(dup)
        }
        return result
    }

    fun find(debt: Debt, recurring: List<Recurring>, loanCategoryId: Long? = null): Recurring? {
        val payment = regularPayment(debt) ?: return null
        val tolerance = maxOf(payment * 2 / 100, MIN_TOLERANCE_KOPECKS)
        val titleWords = DebtMatcher.words(debt.title)
        val placeKey = Places.key(debt.place)
        return recurring
            .filter { abs(it.amountKopecks - payment) <= tolerance }
            .firstOrNull { r ->
                val nameHit = DebtMatcher.words(r.title).any { w -> titleWords.any { DebtMatcher.sameWord(it, w) } }
                val samePlace = placeKey.isNotEmpty() && Places.key(r.place) == placeKey
                nameHit || samePlace || r.categoryId == debt.categoryId || (loanCategoryId != null && r.categoryId == loanCategoryId)
            }
    }

    /** Платёж по договору или первый платёж графика; null — графика нет. */
    internal fun regularPayment(debt: Debt): Long? =
        debt.paymentKopecks?.takeIf { it > 0 } ?: DebtSchedule.of(debt).firstOrNull()?.totalKopecks
}
