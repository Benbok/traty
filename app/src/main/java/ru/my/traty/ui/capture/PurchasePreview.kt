package ru.my.traty.ui.capture

import ru.my.traty.capture.PurchaseOffer
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtSchedule
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatRub

/**
 * Строка под условиями в вопросе «Покупка частями?»: что посчитало приложение.
 * «6 платежей по 10 000 ₽, первый сегодня», «12 платежей по 7 410,76 ₽, переплата 8 929,12 ₽».
 */
internal fun purchasePreview(debt: Debt, offer: PurchaseOffer): String? {
    if (debt.debtKind == DebtKind.PARTIAL) {
        val total = debt.totalKopecks ?: return "Без цены приложение покажет только, сколько уже оплачено."
        val left = (total - offer.amountKopecks).coerceAtLeast(0)
        return "Оплачено ${formatRub(offer.amountKopecks)} из ${formatRub(total)}, осталось ${formatRub(left)}."
    }
    val schedule = DebtSchedule.of(debt)
    if (schedule.isEmpty()) return null
    val first = schedule.first()
    val last = schedule.last()
    val count = "${schedule.size} ${paymentsWord(schedule.size)} по ${formatRub(first.totalKopecks)}"
    val lastNote = if (schedule.size > 1 && last.totalKopecks != first.totalKopecks) ", последний ${formatRub(last.totalKopecks)}" else ""
    val start = if (offer.expenseId != null) "первый сегодня" else "первый ${formatDate(first.date)}"
    val overpay = schedule.sumOf { it.interestKopecks }
    val overpayNote = if (overpay > 0) ". Переплата ${formatRub(overpay)}" else ""
    val mismatch = if (offer.expenseId != null && offer.amountKopecks != first.totalKopecks) {
        ". Сегодня заплачено ${formatRub(offer.amountKopecks)}"
    } else ""
    return "$count$lastNote, $start$overpayNote$mismatch."
}

/** 1 платёж, 2 платежа, 5 платежей. */
internal fun paymentsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "платежей"
        mod10 == 1 -> "платёж"
        mod10 in 2..4 -> "платежа"
        else -> "платежей"
    }
}
