package ru.my.traty.data.debt

import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Одна открытая покупка в сводке долгов.
 * [leftToPayKopecks] — сколько ещё заплатить: оставшаяся цена и проценты по графику; null — цена не названа.
 * [duplicate] — подписка, которая, похоже, повторяет платёж этой покупки (тогда он считается дважды).
 */
data class DebtOverviewLine(
    val debt: Debt,
    val summary: DebtTreeSummary,
    val leftToPayKopecks: Long?,
    val next: NextPayment?,
    val duplicate: Recurring? = null,
)

/**
 * Сводка долгов по рассрочкам, кредитам и покупкам частями на сегодня.
 * [dueSoonKopecks] — сколько внести в ближайшие [dueSoonDays] дней, вместе с просроченным.
 * [subscriptionOnly] — платежи в «Долги и кредиты», заведённые только подпиской: остаток по ним неизвестен.
 */
data class DebtOverview(
    val lines: List<DebtOverviewLine> = emptyList(),
    val closedCount: Int = 0,
    val dueSoonKopecks: Long = 0,
    val dueSoonDays: Int = DebtOverviews.DUE_SOON_DAYS,
    val subscriptionOnly: List<Recurring> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val leftToPayKopecks: Long get() = lines.sumOf { it.leftToPayKopecks ?: 0L }
    val unknownPriceCount: Int get() = lines.count { it.leftToPayKopecks == null }
    val overdueKopecks: Long get() = lines.sumOf { it.summary.overdueKopecks }
    val overdueCount: Int get() = lines.sumOf { it.summary.overdueCount }
    val paidKopecks: Long get() = lines.sumOf { it.summary.paidKopecks }

    /** Кредиты: переплата по всему графику и сколько процентов уже ушло банку. */
    val loans: List<DebtOverviewLine> get() = lines.filter { it.debt.debtKind == DebtKind.LOAN }
    val plannedOverpaymentKopecks: Long get() = loans.sumOf { it.summary.plannedOverpaymentKopecks }
    val paidInterestKopecks: Long get() = loans.sumOf { it.summary.paidInterestKopecks }

    /** Ближайшие платежи по графику, просроченные первыми. */
    val upcoming: List<DebtOverviewLine> get() = lines.filter { it.next != null }.sortedBy { it.next?.date }
    val duplicates: List<DebtOverviewLine> get() = lines.filter { it.duplicate != null }
}

/**
 * Считает [DebtOverview] из покупок, их платежей и подписок. Цифры по каждой покупке берутся из [DebtTree]
 * (а он — из [DebtProgress]), чтобы карточка в «Статистике» совпадала со списком покупок и их деревом.
 * Учитываются только открытые покупки «Я должен»: закрытые и отменённые уже ничего не ждут.
 */
object DebtOverviews {
    const val DUE_SOON_DAYS = 30

    fun of(
        debts: List<Debt>,
        payments: List<DebtPaymentRow>,
        recurring: List<Recurring>,
        loanCategoryId: Long?,
        today: LocalDate,
        zone: ZoneId,
    ): DebtOverview {
        val byDebt = payments.groupBy { it.debtId }
        val mine = debts.filter { it.direction == DebtDirection.I_OWE }
        val trees = mine.map { DebtTree.build(it, byDebt[it.id].orEmpty(), today, zone) }
        val (open, done) = trees.partition { it.debt.state == DebtState.ACTIVE && !it.summary.isPaidOff }
        val subs = recurring.filter { it.state == RecurringState.ACTIVE }
        val dups = DebtDuplicates.matchAll(open.map { it.debt }, subs, loanCategoryId)
        val taken = dups.values.map { it.id }.toSet()
        val lines = open.map { tree ->
            val paid = byDebt[tree.debt.id].orEmpty().map { it.toPaidPart(zone) }
            val next = DebtProgress.of(tree.debt, paid, today).next
            DebtOverviewLine(tree.debt, tree.summary, leftToPay(tree), next, dups[tree.debt.id])
        }
        val dueSoon = open.sumOf { tree ->
            val paid = byDebt[tree.debt.id].orEmpty().map { it.toPaidPart(zone) }
            DebtProgress.dueBetween(tree.debt, paid, today, today.plusDays(DUE_SOON_DAYS - 1L))
        }
        return DebtOverview(
            lines = lines.sortedWith(lineOrder),
            closedCount = done.size,
            dueSoonKopecks = dueSoon,
            subscriptionOnly = subs.filter { it.categoryId == loanCategoryId && it.id !in taken },
        )
    }

    /** Просроченные сверху, потом по дате ближайшего платежа, покупки без графика — по остатку. */
    private val lineOrder = compareByDescending<DebtOverviewLine> { it.summary.overdueCount > 0 }
        .thenBy { it.next?.date ?: LocalDate.MAX }
        .thenByDescending { it.leftToPayKopecks ?: 0L }

    /** Неоплаченные строки графика (тело и проценты) или остаток к доплате у покупки без графика. */
    private fun leftToPay(tree: DebtTree): Long? {
        if (tree.summary.remainingKopecks == null) return null
        return tree.nodes.filter { it.state != TreePaymentState.PAID }.sumOf { it.remainingKopecks }
    }

    private fun DebtPaymentRow.toPaidPart(zone: ZoneId) =
        PaidPart(Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay(), part, amountKopecks)
}
