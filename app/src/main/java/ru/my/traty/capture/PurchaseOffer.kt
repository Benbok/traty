package ru.my.traty.capture

import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.data.Places
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtMatcher
import ru.my.traty.data.normalizeName
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.data.recurring.RecurrencePeriod
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Вопрос после сохранения траты: это доплата за начатую покупку ([match] != null) или новая покупка частями.
 * Ничего не записывается, пока пользователь не нажал «Да».
 *
 * [expenseId] — сохранённая трата-платёж; null — сегодня ничего не платили (взял в кредит), тогда [amountKopecks] = 0.
 */
data class PurchaseOffer(
    val expenseId: Long?,
    val amountKopecks: Long,
    val title: String,
    val place: String?,
    val categoryId: Long,
    val subcategoryId: Long?,
    val day: LocalDate,
    val terms: PurchaseTerms,
    val match: Debt? = null,
) {
    /** Одно и то же предложение не показываем дважды. */
    val key: String get() = expenseId?.let { "e$it" } ?: "t${title.lowercase()}|${terms.total}|$day"
}

/** Что делать с сохранённой тратой. */
sealed interface PurchaseDecision {
    data object None : PurchaseDecision
    data class Attach(val debt: Debt) : PurchaseDecision
    data object Create : PurchaseDecision
}

object PurchaseOffers {
    /**
     * - Доплата («доплатил за холодильник») или трата с названием и магазином начатой покупки — привязать к ней.
     * - Аванс, рассрочка или кредит с ценой или числом платежей — завести новую покупку.
     * - Платёж по кредиту без названия товара и без условий — обычная трата (им займутся «Подписки»).
     */
    fun decide(terms: PurchaseTerms?, note: String?, place: String?, open: List<Debt>, hasPayment: Boolean): PurchaseDecision {
        val byTitle = DebtMatcher.find(open, note, null)
        val best = DebtMatcher.find(open, note, place).firstOrNull()
        val titleHit = best != null && best in byTitle
        val samePlace = best != null && Places.key(place).isNotEmpty() && Places.key(best.place) == Places.key(place)
        return when {
            terms == null -> if (hasPayment && titleHit && samePlace) PurchaseDecision.Attach(checkNotNull(best)) else PurchaseDecision.None
            terms.normalizedKind == PurchaseTerms.TOPUP ->
                if (hasPayment && best != null) PurchaseDecision.Attach(best) else PurchaseDecision.None
            // «очередной платёж по рассрочке за телевизор» — без новой цены это та же покупка
            hasPayment && titleHit && terms.total == null -> PurchaseDecision.Attach(checkNotNull(best))
            terms.normalizedKind == PurchaseTerms.PARTIAL && hasPayment -> PurchaseDecision.Create
            terms.total != null || terms.count != null -> PurchaseDecision.Create
            else -> PurchaseDecision.None
        }
    }

    /** Название новой покупки: товар из заметки, иначе подкатегория или магазин. */
    fun titleOf(note: String?, subcategory: String?, place: String?): String =
        listOf(note, subcategory, place).firstOrNull { !it.isNullOrBlank() }?.let { normalizeName(it) } ?: "Покупка"
}

/**
 * Поля вопроса «Покупка частями?»: вид и условия текстом, как их правят на экране.
 * [period]: "month", "week" или "2weeks".
 */
data class PurchaseForm(
    val kind: String,
    val total: String,
    val count: String,
    val rate: String,
    val period: String = "month",
) {
    /** Покупка для сохранения или null, если условий не хватает (у рассрочки и кредита нужно число платежей). */
    fun toDebt(offer: PurchaseOffer): Debt? {
        val kind = when (kind) {
            PurchaseTerms.INSTALLMENT -> DebtKind.INSTALLMENT
            PurchaseTerms.LOAN -> DebtKind.LOAN
            else -> DebtKind.PARTIAL
        }
        val totalKopecks = total.takeIf { it.isNotBlank() }?.let { parsePositiveKopecks(it) ?: return null }
        val count = count.trim().takeIf { it.isNotEmpty() }?.let { c -> c.toIntOrNull()?.takeIf { it in 1..600 } ?: return null }
        val rate = rate.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { r -> r.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal(100) } ?: return null }
        if (kind != DebtKind.PARTIAL && (count == null || totalKopecks == null)) return null
        if (kind == DebtKind.PARTIAL && totalKopecks == null && offer.expenseId == null) return null
        val period = periodOf(this.period)
        // Заплатили сегодня — это первый платёж графика; не платили — первый через период
        val first = if (offer.expenseId != null) offer.day else period.advance(offer.day)
        return Debt(
            title = offer.title,
            place = offer.place,
            categoryId = offer.categoryId,
            subcategoryId = offer.subcategoryId,
            kind = kind.key,
            totalKopecks = totalKopecks,
            rateBasisPoints = if (kind == DebtKind.LOAN) rate?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt() ?: 0 else 0,
            paymentsCount = if (kind == DebtKind.PARTIAL) null else count,
            periodUnit = period.unit.key,
            periodEvery = period.every,
            firstDueEpochDay = if (kind == DebtKind.PARTIAL) null else first.toEpochDay(),
        )
    }

    companion object {
        fun from(offer: PurchaseOffer): PurchaseForm {
            val t = offer.terms
            return PurchaseForm(
                kind = t.normalizedKind.takeIf { it != PurchaseTerms.TOPUP } ?: PurchaseTerms.PARTIAL,
                total = t.total?.let { plain(it) }.orEmpty(),
                count = t.count?.toString().orEmpty(),
                rate = t.rate?.let { plain(it) }.orEmpty(),
                period = when (t.period) { "week", "2weeks" -> t.period; else -> "month" },
            )
        }

        fun periodOf(key: String): RecurrencePeriod = when (key) {
            "week" -> RecurrencePeriod(PeriodUnit.WEEK)
            "2weeks" -> RecurrencePeriod(PeriodUnit.WEEK, 2)
            else -> RecurrencePeriod.MONTHLY
        }

        private fun plain(v: Double): String = BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
    }
}
